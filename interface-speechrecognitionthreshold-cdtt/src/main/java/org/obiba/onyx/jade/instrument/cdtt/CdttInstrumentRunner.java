package org.obiba.onyx.jade.instrument.cdtt;

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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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

  private static String RESOURCE_BUNDLE_BASE_NAME = "speechrecognitionthreshold-instrument";

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
    this.gender = instrumentExecutionService.getInputParameterValue("INPUT_PARTICIPANT_GENDER").getValue().equals("M") ? "Male" : "Female";
    this.barcode = instrumentExecutionService.getInputParameterValue("INPUT_PARTICIPANT_BARCODE").getValue();
    this.language = instrumentExecutionService.getInputParameterValue("INPUT_PARTICIPANT_LANGUAGE").getValue().equals("ENGLISH") ? "EN_CA" : "FR_CA";
    this.testear = instrumentExecutionService.getInputParameterValue("INPUT_CDTT_TEST_EAR").getValue();

    HashMap<String,String> map = new HashMap<String,String>();
    map.put("Default test language",this.language);
    map.put("Default talker",this.gender);
    map.put("Default test ear: Left (0), right (1), or binaural (2)",this.testear);
    Set<Map.Entry<String,String>> set = map.entrySet();

    File file = getSettingPath() + "/Settings.xlsx";
    FileInputStream instream = new FileInputStream(file);
    XSSFWorkbook workbook = new XSSFWorkbook(instream);
    XSSFSheet sheet = workbook.getSheet("DefaultParameters");

    for(Map.Entry<String,String> entry : set) {
      String key = entry.getKey();
      boolean found = false;
      Iterator<Row> rowIterator = sheet.iterator();
      while (rowIterator.hasNext() && !found) {
        Row row = rowIterator.next();
        Cell cell = row.getCell(0);
        if(cell.getStringCellValue().equals(key)) {
          cell = row.getCell(1);
          cell.setValue(entry.getValue());
          found = true;
        }
      }
    }

    try {
      FileOutputStream outstream = new FileOutputStream(file);
      workbook.write(outstream);
    }
    workbook.close();
  }

  public void run() {
    externalAppHelper.launch();
    getDataFile();
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
   * popup when no test key is found or when codes are missing in result file
   */
  public void getDataFile() {
    File file = getResutPath() + "/Results-" + this.barcode + ".xlsx";

    if(!(file.exists() && file.isFile())) {
      warningPopup("noResultFileFound");
      log.warn("CDTT has been shutdown but the result file was not found. Perhaps CDTT was shutdown before the test was started.");
    } else {

      HashSet<String> results = extractTestsFromResultFile(file);

      if(results.isEmpty()) {
        warningPopup("noTestKey");
        log.warn("No test data was found in the Cdtt test result file. Perhaps Cdtt was shutdown before the first test completed.");
      } else {

        EventQueue.invokeLater(new Runnable() {
          public void run() {
            String msg = resourceBundleMessageSource.getMessage("uploading", null, getLocale());
            String title = resourceBundleMessageSource.getMessage("uploadingTitle", null, getLocale());
            JOptionPane.showMessageDialog(null, msg, title, JOptionPane.INFORMATION_MESSAGE);
          }
        });

        Data binaryData = DataBuilder.buildBinary(file);
        sendDataToServer(binaryData);

        Set<CdttTests> completedTests = getTestsCompleted(resultTests);

        if(missingTests.isEmpty() == false) warningPopup("missingTestKey", new String[] { formatToString(missingTestNames) });
      }
    }
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
  HashSet<String> extractTestsFromResultFile(File resultFile) {
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
