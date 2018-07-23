package org.obiba.onyx.jade.instrument.cdtt;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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

  private String packagePrefix;

  private Locale locale;

  private ResourceBundleMessageSource resourceBundleMessageSource = new ResourceBundleMessageSource();

  private String resultFilenamePrefix;

  public void initialize() {
    if(isConfigFileAndResultFileLocked()) {
      String errorMessage = warningPopup("cdttLocked");
      log.error(errorMessage);
      System.exit(1); // Leave now. Avoid deleting in use data files.
    }
    deleteDeviceData();
  }

  public void run() {
    StringBuilder params = new StringBuilder();

    // Generate fake participant id from current date-time
    SimpleDateFormat formatter = new SimpleDateFormat("yyMMddHHmm");
    Date currentTime = new Date(System.currentTimeMillis());
    String fakeId = formatter.format(currentTime);

    params.append(" /u" + fakeId);

    params.append(" /i" + instrumentExecutionService.getInstrumentOperatorUsername());

    params.append(" /c" + CLINIC_NAME + "_");

    String language = instrumentExecutionService.getInputParameterValue("COGNITIVE_TEST_LANGUAGE").getValueAsString().equals("FRENCH") ? "F" : "E";
    params.append(" /l" + language);

    externalAppHelper.setParameterStr(params.toString());
    externalAppHelper.launch();
    getDataFiles();
  }

  public void shutdown() {
    deleteDeviceData();
    releaseConfigFileAndResultFileLock();
  }

  /**
   * Gets the data in the result file, compares the test codes obtained to the configuration file and show a warning
   * popup when no test key is found or when codes are missing in result file
   */
  public void getDataFiles() {
    List<File> resultFiles = new ArrayList<File>();

    for(File file : (new File(getResultPath())).listFiles()) {
      if(file.getName().contains(resultFilenamePrefix)) resultFiles.add(file);
    }

    if(resultFiles.size() == 0) {
      warningPopup("noResultFileFound");
      log.warn("Cdtt has been shutdown but the result file was not found. Perhaps Cdtt was shutdown before the test was started.");
    } else if(resultFiles.size() > 1) {
      // TODO: Handle multiple result files
      log.info("More than one result file found");
    } else {
      HashSet<String> resultTests = extractTestsFromResultFile(resultFiles.get(0), new LineCallback() {
        public String handleLine(String line) {
          return line.substring(0, 2);
        }
      });

      if(resultTests.isEmpty()) {
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

        Data binaryData = DataBuilder.buildBinary(resultFiles.get(0));
        sendDataToServer(binaryData);

        Set<CdttTests> completedTests = getTestsCompleted(resultTests);
        Set<CdttTests> configuredTests = getTestsConfiguredToRunFromNoodleConfigurationFile();

        Set<CdttTests> missingTests = getMissingTests(configuredTests, completedTests);
        Set<String> missingTestNames = getLocalizedTestNames(missingTests);

        if(missingTests.isEmpty() == false) warningPopup("missingTestKey", new String[] { formatToString(missingTestNames) });
      }
    }
  }

  private Set<CdttTests> getTestsConfiguredToRunFromNoodleConfigurationFile() {
    Set<String> testsConfiguredToRun = extractTestsFromResultFile(new File(getSoftwareInstallPath() + File.separator + NODDLE_CONFIG_FILENAME), new LineCallback() {
      public String handleLine(String line) {
        if(!line.startsWith("!") && !line.startsWith("X")) return (line.substring(0, 2));
        return null;
      }
    });
    Set<CdttTests> configuredSet = new HashSet<CdttTests>(testsConfiguredToRun.size());
    for(String testString : testsConfiguredToRun) {
      configuredSet.add(CdttTests.valueOf(testString));
    }
    return configuredSet;
  }

  private Set<CdttTests> getMissingTests(Set<CdttTests> configuredTests, Set<CdttTests> completedTests) {
    for(CdttTests test : completedTests) {
      if(configuredTests.contains(test)) {
        configuredTests.remove(test);
      }
    }
    return configuredTests;
  }

  private Set<String> getLocalizedTestNames(Set<CdttTests> tests) {

    Set<String> localizedTestNames = new HashSet<String>(tests.size());
    for(CdttTests test : tests) {
      localizedTestNames.add(resourceBundleMessageSource.getMessage(test.getAssetKey(), null, getLocale()));
    }
    return localizedTestNames;
  }

  Set<CdttTests> getTestsCompleted(Set<String> resultTests) {
    Set<CdttTests> testsCompleted = new HashSet<CdttTests>(resultTests.size());
    for(String codeAsString : resultTests) {
      Integer code = Integer.valueOf(codeAsString); // Throws NumberFormatException.
      if(endDataCodeMap.containsKey(code)) {
        testsCompleted.add(endDataCodeMap.get(code));
      }
    }
    return testsCompleted;
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
  HashSet<String> extractTestsFromResultFile(File resultFile, LineCallback callback) {
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
    // Delete resultPath files if any exist.
    File resultDir = new File(getResultPath());

    try {
      for(File file : resultDir.listFiles()) {
        if(!FileUtil.delete(file)) log.warn("Could not delete CdttTest result file [" + file.getAbsolutePath() + "].");
      }
    } catch(IOException ex) {
      log.error("Could not delete CdttTest result file: " + ex);
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

  public void setResultPath(String resultPath) {
    this.resultPath = resultPath;
  }

  public String getResultPath() {
    return resultPath;
  }

  public String getSoftwareInstallPath() {
    return softwareInstallPath;
  }

  public String getPackagePrefix() {
    return packagePrefix;
  }

  public void setPackagePrefix(String packagePrefix) {
    this.packagePrefix = packagePrefix + "_";
    resultFilenamePrefix = this.packagePrefix + CLINIC_NAME + "_";
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

  interface LineCallback {
    public String handleLine(String line);
  }

  /**
   * Initialise instrument runner after all properties are set. Prevents life cycle execution if values do not validate.
   * @throws CdttTestInstrumentRunnerException thrown when property validation fails.
   */
  public void initializeCdttTestInstrumentRunner() throws CdttTestInstrumentRunnerException {
    initializeResourceBundle();
    initializeEndDataCodeMap();
    validateSoftwareInstallPathExists();
    validateResultPathExists();
  }

  private void initializeResourceBundle() {
    resourceBundleMessageSource.setBasename(RESOURCE_BUNDLE_BASE_NAME);
  }

  void initializeEndDataCodeMap() {
    endDataCodeMap = new HashMap<Integer, CdttTests>();
    for(CdttTests cdttTest : CdttTests.values()) {
      endDataCodeMap.put(cdttTest.getEndDataCode(), cdttTest);
    }
  }

  private void validateSoftwareInstallPathExists() throws CdttTestInstrumentRunnerException {
    File path = new File(this.softwareInstallPath);
    if(!path.exists()) {
      String errorMessage = warningPopup("cdttInstallationDirectoryMissing", new String[] { path.getAbsolutePath() });
      log.error(errorMessage);
      throw new CdttTestInstrumentRunnerException(errorMessage);
    }
  }

  private void validateResultPathExists() throws CdttTestInstrumentRunnerException {
    File path = new File(this.resultPath);
    if(!path.exists()) {
      String errorMessage = warningPopup("cdttResultsDirectoryMissing", new String[] { path.getAbsolutePath() });
      log.error(errorMessage);
      throw new CdttTestInstrumentRunnerException(errorMessage);
    }
  }

  boolean isConfigFileAndResultFileLocked() {
    File wFile = new File(System.getProperty("java.io.tmpdir"), "CdttConfigAndResultFile.lock");
    try {
      FileChannel channel = new RandomAccessFile(wFile, "rw").getChannel();

      try {
        configAndResultFileLock = channel.tryLock();
      } catch(OverlappingFileLockException wEx) {
        return true;
      }

      if(configAndResultFileLock == null) {
        return true;
      } else {
        return false;
      }

    } catch(Exception wCouldNotDetermineIfRunning) {
      wCouldNotDetermineIfRunning.printStackTrace();
      return true;
    }

  }

  void releaseConfigFileAndResultFileLock() {
    if(configAndResultFileLock == null) {
      log.warn("We do not own the Cdtt config and result file lock, yet we are attempting to release it.");
    } else {
      try {
        configAndResultFileLock.release();
      } catch(IOException e) {
        log.error("Unable to release Cdtt config and result file lock. " + e);
      }
    }
  }
}
