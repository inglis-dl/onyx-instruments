package org.obiba.onyx.jade.instrument.cdtt;

import org.obiba.onyx.jade.instrument.cdtt.CellHelper;
import org.obiba.onyx.jade.instrument.cdtt.CellHelper.Action;
import org.obiba.onyx.jade.instrument.cdtt.CellHelper;
import org.obiba.onyx.jade.instrument.cdtt.WorkbookSheet;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import javax.swing.JOptionPane;

import org.obiba.onyx.jade.client.JnlpClient;
import org.obiba.onyx.jade.instrument.ExternalAppLauncherHelper;
import org.obiba.onyx.jade.instrument.InstrumentRunner;
import org.obiba.onyx.jade.instrument.service.InstrumentExecutionService;
import org.obiba.onyx.util.FileUtil;
import org.obiba.onyx.util.UnicodeReader;
import org.obiba.onyx.util.data.Data;
import org.obiba.onyx.util.data.DataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Launches, configures and collects data from Canadian Digit Triplet Test native application.
 */
public class CdttTestInstrumentRunner implements InstrumentRunner {

  private static final Logger log = LoggerFactory.getLogger(JnlpClient.class);

  protected ExternalAppLauncherHelper externalAppHelper;

  protected InstrumentExecutionService instrumentExecutionService;

  protected FileLock configAndResultFileLock;

  private String softwareInstallPath;

  private String resultPath;

  private String settingPath;

  private Locale locale;

  private static String RESOURCE_BUNDLE_BASE_NAME = "srt-instrument";

  private ResourceBundleMessageSource resourceBundleMessageSource = new ResourceBundleMessageSource();

  private String gender;

  private String barcode;

  private String language;

  private String testear;

  public void initialize() {
    log.info("Initializing CDTT");
    createBackupSettingsFile();
    intitializeSettingsFile();
  }

  public void initializeSettingsFile() {
    this.gender = instrumentExecutionService.getInputParameterValue(
      "INPUT_PARTICIPANT_GENDER").getValue().equals("M") ? "Male" : "Female";
    this.barcode = instrumentExecutionService.getInputParameterValue(
      "INPUT_PARTICIPANT_BARCODE").getValue();
    this.language = instrumentExecutionService.getInputParameterValue(
      "INPUT_PARTICIPANT_LANGUAGE").getValue().equals("ENGLISH") ? "EN_CA" : "FR_CA";
    this.testear = instrumentExecutionService.getInputParameterValue(
      "INPUT_CDTT_TEST_EAR").getValue();

    HashMap<String,CellHelper> map = new HashMap<String,CellHelper>();
    map.put( "Default test language",
      new CellHelper(1,this.language,Action.WRITE));
    map.put( "Default talker",
      new CellHelper(1,this.gender,Action.WRITE));
    map.put( "Default test ear: Left (0), right (1), or binaural (2)",
      new CellHelper(1,this.testear,Action.WRITE));

    String fileName = getSettingPath() + "/Settings.xlsx";
    WorkbookSheet settings = new WorkbookSheet(fileName,"DefaultParameters");

    try {
      settings.writeSheet(map);
    } catch (...) {  //TODO catch the exception and throw ?
    }
  }

  public void run() {
    log.info("Launching CDTT application");
    externalAppHelper.launch();

    log.info("Retrieving measurements");
    Map<String, Data> data = retrieveDeviceData();

    log.info("Sending data to server");
    sendDataToServer(data);
  }

  public void sendDataToServer(Map<String, Data> data) {
    instrumentExecutionService.addOutputParameterValues(data);
  }

  /**
   * Implements parent method shutdown from InstrumentRunner Delete results from current measurement
   */
  @Override
  public void shutdown() {
    deleteDeviceData();
    restoreSettingsFile();
  }

  /**
   * Gets the data in the result file, compares the test codes obtained to the configuration file and show a warning
   * popup when no test data is found or when the identifier does not match the barcode
   */
  public Map<String, Data>  retrieveDeviceData() {

    Map<String, Data> outputData = new HashMap<String, Data>();

    String fileName = getResutPath() + "/Results-" + this.barcode + ".xlsx";

    WorkbookSheet settings = new WorkbookSheet(fileName,"Main");

    if(!sheet.canRead()) {
      warningPopup("noResultFileFound");
      log.warn("CDTT has been shutdown but the result file was not found. Perhaps CDTT was shutdown before the test was started.");
    } else {

      // map of keys and cell offset directions:
      // 1 = east of current cell (ie., beside current cell in same row)
      // 2 = south of current cell (ie., below current cell in same column)
      //
      HashMap<String,CellHelper> map = new HashMap<String,CellHelper>();
      map.put("Subject ID:",
        new CellHelper(1,this.barcode,Action.VALIDATE));
      map.put("Date & time",
        new CellHelper(2,"RES_TEST_DATETIME",Action.READ));
      map.put("Language",
        new CellHelper(2,"RES_TEST_LANGUAGE",Action.READ));
      map.put("Talker",
        new CellHelper(2,"RES_TEST_TALKER",Action.READ));
      map.put("Test Ear",
        new CellHelper(2,"RES_TEST_EAR",Action.READ));
      map.put("SRT",
        new CellHelper(2,"RES_SRT",Action.READ));
      map.put("St. Dev.",
        new CellHelper(2,"RES_STD_DEV",Action.READ));
      map.put("Reversals",
        new CellHelper(2,"RES_REV_NB",Action.READ));

      try {
        settings.readSheet(map);
      } catch (...) {  //TODO catch the exception and throw ?
      }



      if(results.isEmpty()) {
        warningPopup("noTestData");
        log.warn("No test data was found in the CDTT test result file. Perhaps CDTT was shutdown before the test completed.");
      } else {

        EventQueue.invokeLater(new Runnable() {
          public void run() {
            String msg = resourceBundleMessageSource.getMessage("uploading", null, getLocale());
            String title = resourceBundleMessageSource.getMessage("uploadingTitle", null, getLocale());
            JOptionPane.showMessageDialog(null, msg, title, JOptionPane.INFORMATION_MESSAGE);
          }
        });

        Data binaryData = DataBuilder.buildBinary(new File(fileName));
        sendDataToServer(binaryData);
      }
    }

    return outputData;
  }

  public void sendDataToServer(Data binaryData) {
    Map<String, Data> ouputToSend = new HashMap<String, Data>();

    // Save the Result File
    try {
      ouputToSend.put("RESULT_FILE", binaryData);
    } catch(Exception e) {
      log.warn("No device output file found");
    }
    instrumentExecutionService.addOutputParameterValues(ouputToSend);
  }

  /**
   * Function that opens the specified file and gets the testcode Uses a LineCallback implementation to act differently
   * on the files passed as parameter
   * @param resultFile
   * @param callback
   * @return
   */
  private Map<String, Data> extractDataFromResultFile(File resultFile) {
    HashSet<String> testCodes = new HashSet<String>();
    InputStream resultFileStrm = null;
    UnicodeReader resultReader = null;
    BufferedReader fileReader = null;

    try {
      resultFileStrm = new FileInputStream(resultFile);
      resultReader = new UnicodeReader(resultFileStrm);
      fileReader = new BufferedReader(resultReader);
      String line;

      while((line = fileReader.readLine()) != null) {
        if(line.isEmpty() == false && line.startsWith("#") == false) {
          String testCode = callback.handleLine(line);
          if(testCode != null) testCodes.add(testCode);
        }
      }

      resultFileStrm.close();
      fileReader.close();
      resultReader.close();
    } catch(FileNotFoundException fnfEx) {
      log.warn("No device output found");
    } catch(IOException ioEx) {
      throw new RuntimeException("Error: retrieve cognitive test data IOException", ioEx);
    } catch(Exception ex) {
      throw new RuntimeException("Error: retrieve cognitive test data", ex);
    }

    return testCodes;
  }

  String warningPopup(String key, String[] args) {
    String message = resourceBundleMessageSource.getMessage(key, args, getLocale());
    String title = resourceBundleMessageSource.getMessage("warningTitle", null, getLocale());

    JOptionPane.showMessageDialog(null, message, title, JOptionPane.WARNING_MESSAGE);
    return message;
  }

  String warningPopup(String key) {
    return warningPopup(key, null);
  }

  private String formatToString(Set<String> strings) {
    String formattedString = "";
    if(strings != null) {
      for(String item : strings) {
        formattedString += "\n" + item;
      }
    }
    return formattedString;
  }

  private void deleteDeviceData() {
    // Delete result files if any exist.
    File resultDir = new File(getResultPath());
    try {
      for(File file : resultDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return !name.endsWith("Template.xlsx");
        }
      })) {
        if(!FileUtil.delete(file)) log.warn("Could not delete CDTT result file [" + file.getAbsolutePath() + "].");
      }
    } catch(IOException ex) {
      log.error("Could not delete CDTT result file: " + ex);
    }
  }

  public ExternalAppLauncherHelper getExternalAppHelper() {
    return externalAppHelper;
  }

  public void setExternalAppHelper(ExternalAppLauncherHelper externalAppHelper) {
    this.externalAppHelper = externalAppHelper;
  }

  public void setInstrumentExecutionService(InstrumentExecutionService instrumentExecutionService) {
    this.instrumentExecutionService = instrumentExecutionService;
  }

  public void setSoftwareInstallPath(String softwareInstallPath) {
    this.softwareInstallPath = softwareInstallPath;
  }

  public String getSoftwareInstallPath() {
    return softwareInstallPath;
  }

  public void setResultPath(String resultPath) {
    this.resultPath = resultPath;
  }

  public String getResultPath() {
    return resultPath;
  }

  public void setSettingPath(String settingPath) {
    this.settingPath = settingPath;
  }

  public String getSettingPath() {
    return settingPath;
  }

  public Locale getLocale() {
    return locale;
  }

  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  public void setResourceBundleMessageSource(ResourceBundleMessageSource resourceBundleMessageSource) {
    this.resourceBundleMessageSource = resourceBundleMessageSource;
  }

  /**
   * Initialise instrument runner after all properties are set. Prevents life cycle execution if values do not validate.
   */
  public void initializeCdttTestInstrumentRunner() {
    initializeResourceBundle();
    validateSoftwareInstallPathExists();
    validateSettingPathExists();
    validateResultPathExists();
  }

  private void initializeResourceBundle() {
    resourceBundleMessageSource.setBasename(RESOURCE_BUNDLE_BASE_NAME);
  }

  private void validateSoftwareInstallPathExists() {
    File path = new File(this.softwareInstallPath);
    if(!path.exists()) {
      String errorMessage = warningPopup("cdttInstallationDirectoryMissing", new String[] { path.getAbsolutePath() });
      log.error(errorMessage);
      throw new RuntimeException(errorMessage);
    }
  }

  private void validateResultPathExists() {
    File path = new File(this.resultPath);
    if(!path.exists()) {
      String errorMessage = warningPopup("cdttResultsDirectoryMissing", new String[] { path.getAbsolutePath() });
      log.error(errorMessage);
      throw new RuntimeException(errorMessage);
    }
  }

  private void validateSettingPathExists() {
    File path = new File(this.settingPath);
    if(!path.exists()) {
      String errorMessage = warningPopup("cdttSettingsDirectoryMissing", new String[] { path.getAbsolutePath() });
      log.error(errorMessage);
      throw new RuntimeException(errorMessage);
    }
  }

  /**
   *  back up existing settings file
   */
  private void createBackupSettingsFile() {
    File file = getSettingPath() + "/Settings.xlsx";
    File backupFile = new File( file.getAbsoluteFile() + ".orig" );
    try {
      if(file.exists() && !file.isDirectory()) {
        log.info("backing up existing settings file");
        FileUtil.copyFile(file,backupFile);
      }
    } catch(Exception e) {
      throw new RuntimeException("Error backing up CDTT " + file.getAbsoluteFile() + " file", e);
    }
  }

  /**
   *  restore backed up settings file
   */
  private void restoreSettingsFile() {
    File file = getSettingPath() + "/Settings.xlsx";
    File backupFile = new File( file.getAbsoluteFile() + ".orig" );
    try {
      if(backupFile.exists()) {
        log.info("restoring pre-existing settings file {} => {}",
          backupFile.getAbsoluteFile(), file.getAbsoluteFile());
        FileUtil.copyFile(backupFile,file);
        backupFile.delete();
      }
    } catch(Exception e) {
      throw new RuntimeException("Error restoring CDTT " + file.getAbsoluteFile() + " file", e);
    }
  }
}
