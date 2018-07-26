package org.obiba.onyx.jade.instrument.cdtt;

import org.obiba.onyx.jade.instrument.cdtt.CellHelper;
import org.obiba.onyx.jade.instrument.cdtt.CellHelper.Action;
import org.obiba.onyx.jade.instrument.cdtt.WorkbookSheet;

import java.awt.EventQueue;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import java.text.ParseException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.JOptionPane;

import org.obiba.onyx.jade.client.JnlpClient;
import org.obiba.onyx.jade.instrument.ExternalAppLauncherHelper;
import org.obiba.onyx.jade.instrument.InstrumentRunner;
import org.obiba.onyx.jade.instrument.service.InstrumentExecutionService;
import org.obiba.onyx.util.FileUtil;
import org.obiba.onyx.util.data.Data;
import org.obiba.onyx.util.data.DataBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.InitializingBean;

/**
 * Launches, configures and collects data from Canadian Digit Triplet Test native application.
 */
public class CdttInstrumentRunner implements InstrumentRunner, InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(JnlpClient.class);

  protected ExternalAppLauncherHelper externalAppHelper;

  protected InstrumentExecutionService instrumentExecutionService;

  private String softwareInstallPath;

  private String resultPath;

  private String settingPath;

  private Locale locale;

  private static String RESOURCE_BUNDLE_BASE_NAME = "srt-instrument";

  private ResourceBundle cdttResourceBundle;

  public void afterPropertiesSet() throws Exception {
    setCdttResourceBundle(ResourceBundle.getBundle(RESOURCE_BUNDLE_BASE_NAME, getLocale()));
  }

  /**
   * Initialize the instrument runner
   */
  public void initialize() {
    log.info( "Initializing CDTT" );
    createBackupSettingsFile();
    initializeSettingsFile();
    externalAppHelper.setParameterStr(instrumentExecutionService.getParticipantID());
  }

  /**
   * Initialize the Settings.xlsx file with participant data as defined
   * in instrument-context.xml
   */
  public void initializeSettingsFile() {
    String gender = instrumentExecutionService.getInputParameterValue(
      "INPUT_PARTICIPANT_GENDER").getValue();
    gender = gender.toLowerCase().startsWith("m") ? "Male" : "Female";
    String language = instrumentExecutionService.getInputParameterValue(
      "INPUT_PARTICIPANT_LANGUAGE").getValue();
    language = language.toLowerCase().startsWith("e") ? "EN_CA" : "FR_CA";
    String testear = instrumentExecutionService.getInputParameterValue(
      "INPUT_CDTT_TEST_EAR").getValue().toString();

    HashMap<String, CellHelper> map = new HashMap<String, CellHelper>();
    map.put( "Default test language",
      new CellHelper( 1, language, Action.WRITE ) );
    map.put( "Default talker",
      new CellHelper( 1, gender, Action.WRITE ));
    map.put( "Default test ear: Left (0), right (1), or binaural (2)",
      new CellHelper( 1, testear, Action.WRITE ) );

    WorkbookSheet settings = null;
    RuntimeException rte = null;
    try {
      String fileName = getSettingsFile();
      settings = new WorkbookSheet( fileName, "DefaultParameters" );
      settings.write( map );
    } catch( IOException e ) {
      rte = new RuntimeException(e);
    } catch( ParseException e) {
      rte = new RuntimeException(e);
    }
    if( null != rte ) throw rte;
  }

  /**
   * Run the CDTT application
   */
  public void run() {
    log.info( "Launching CDTT application" );
    externalAppHelper.launch();

    log.info( "Retrieving measurements" );
    Map<String, Data> data = retrieveDeviceData();

    log.info( "Sending data to server" );
    sendDataToServer(data);
  }

  /**
   * Send the data to the server
   * @param data a map of variable labels and Data values
   */
  public void sendDataToServer( Map<String, Data> data ) {
    instrumentExecutionService.addOutputParameterValues(data);
  }

  /**
   * Implements parent method shutdown from InstrumentRunner. Delete results from current measurement
   */
  @Override
  public void shutdown() {
    deleteDeviceData();
    restoreSettingsFile();
  }

  /**
   * Gets the data in the result file, compares the identifier obtained in the configuration file and show a warning
   * popup when no test data is found or when the subject ID does not match the participant barcode
   * @return the map of variable names and data
   */
  public Map<String, Data> retrieveDeviceData() {

    Map<String, Data> outputData = new HashMap<String, Data>();

    String fileName = getResultsFile();
    WorkbookSheet results = new WorkbookSheet( fileName, "Main" );

    if( !results.canRead() ) {
      warningPopup( "noResultFileFound" );
      log.warn( "CDTT has been shutdown but the result file was not found." );
    } else {

      String [] ear = { "Left", "Right", "Binaural" };

      HashMap<String, CellHelper> map = new HashMap<String, CellHelper>();
      String gender = instrumentExecutionService.getInputParameterValue(
        "INPUT_PARTICIPANT_GENDER").getValue();
      gender = gender.toLowerCase().startsWith("m") ? "Male" : "Female";
      String barcode = instrumentExecutionService.getParticipantID();
      String language = instrumentExecutionService.getInputParameterValue(
        "INPUT_PARTICIPANT_LANGUAGE").getValue();
      language = language.toLowerCase().startsWith("e") ? "EN_CA" : "FR_CA";
      int testear = instrumentExecutionService.getInputParameterValue(
        "INPUT_CDTT_TEST_EAR").getValue();

      log.info("CDTT language: " + language );

      map.put( "Subject ID:",
        new CellHelper( 1, barcode, Action.VALIDATE) );
      map.put( "Language",
        new CellHelper( 2, language, Action.VALIDATE) );
      map.put( "Talker",
        new CellHelper( 2, gender, Action.VALIDATE) );
      map.put( "Test Ear",
        new CellHelper( 2, ear[testear], Action.VALIDATE) );

      boolean valid = true;
      try {
        results.read( map );
      } catch( IOException e )  {
        warningPopup( "problemResultFile" );
        log.warn( "failed to read the results file " + fileName + ": " + e.getMessage() );
        valid = false;
      } catch( ParseException e ) {
        warningPopup( "invalidTestData" );
        log.warn( "failed to validate the results file " + fileName + ": " + e.getMessage() );
        valid = false;
      }

      if(valid) {
	map.clear();

	map.put( "Date & time",
	  new CellHelper( 2, "RES_TEST_DATETIME", Action.READ) );
	map.put( "Language",
	  new CellHelper( 2, "RES_TEST_LANGUAGE", Action.READ) );
	map.put( "Talker",
	  new CellHelper( 2, "RES_TEST_TALKER", Action.READ) );
	map.put( "Test Ear",
	  new CellHelper( 2, "RES_TEST_EAR", Action.READ) );
	map.put( "SRT",
	  new CellHelper( 2, "RES_SRT", Action.READ) );
	map.put( "St. Dev.",
	  new CellHelper( 2, "RES_STD_DEV", Action.READ) );
	map.put( "Reversals",
	  new CellHelper( 2, "RES_REV_NB", Action.READ) );

	try {
	  outputData.putAll(results.read(map));
	} catch( Exception e )  {
	  log.warn( "failed to read the results file " + fileName + ": " + e.getMessage() );
	}

	if( outputData.isEmpty() ) {
	  warningPopup( "noTestData" );
	  log.warn( "No test data was found in the CDTT test result file." );
	} else {

	  EventQueue.invokeLater(new Runnable() {
	    public void run() {
              try {
                String message = cdttResourceBundle.getString( "uploading" );
	        String title = cdttResourceBundle.getString( "uploadingTitle" );
	        JOptionPane.showMessageDialog( null, message, title, JOptionPane.INFORMATION_MESSAGE );
              } catch (Exception e) {
                log.warn("The data upload message dialog failed to launch");
              }
	    }
	  });

	  outputData.put("RESULT_FILE", DataBuilder.buildBinary(new File( fileName )));
	}
      }
    }

    return outputData;
  }

  /**
   * Present an informative message dialog
   * @param key a resource string key
   * @return the warning message
   */
  String warningPopup(String key) {
    String message = cdttResourceBundle.getString( key );
    String title = cdttResourceBundle.getString( "warningTitle" );

    JOptionPane.showMessageDialog( null, message, title, JOptionPane.WARNING_MESSAGE );
    return message;
  }

  /**
   * Delete residual results files
   */
  private void deleteDeviceData() {
    // Delete result files if any exist.
    File resultDir = new File( getResultPath() );
    try {
      for(File file : resultDir.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return !name.endsWith("Template.xlsx");
        }
      })) {
        if( !FileUtil.delete(file) )
          log.warn("Could not delete CDTT result file [" + file.getAbsolutePath() + "].");
      }
    } catch( IOException e ) {
      log.error( "Could not delete CDTT result file: " + e.getMessage() );
    }
  }

  /**
   * Get the external application launcher helper
   * @return the external application launcher helpe
   */
  public ExternalAppLauncherHelper getExternalAppHelper() {
    return externalAppHelper;
  }

  /**
   * Set the external application launcher helper
   * @param externalAppHelper the external application launcher helpe
   */
  public void setExternalAppHelper(ExternalAppLauncherHelper externalAppHelper) {
    this.externalAppHelper = externalAppHelper;
  }

  /**
   * Set the instrument execution service
   * @param instrumentExecutionService the instrument execution service
   */
  public void setInstrumentExecutionService(InstrumentExecutionService instrumentExecutionService) {
    this.instrumentExecutionService = instrumentExecutionService;
  }

  /**
   * Set the software installation path
   * @param softwareInstallPath the software installation path
   */
  public void setSoftwareInstallPath(String softwareInstallPath) {
    this.softwareInstallPath = softwareInstallPath;
  }

  /**
   * Get the software installation path
   * @return the software installation path
   */
  public String getSoftwareInstallPath() {
    return softwareInstallPath;
  }

  /**
   * Set the path to the results file
   * @param resultPath the path to the results file
   */
  public void setResultPath(String resultPath) {
    this.resultPath = resultPath;
  }

  /**
   * Get the results file path
   * @return the results file path
   */
  public String getResultPath() {
    return resultPath;
  }

  /**
   * Set the path to the settings file
   * @param settingPath the path to the settings file
   */
  public void setSettingPath(String settingPath) {
    this.settingPath = settingPath;
  }

  /**
   * Get the settings file path
   * @return the settings file path
   */
  public String getSettingPath() {
    return settingPath;
  }

  /**
   * Get the settings file name
   * @return the settings file name
   */
  public String getSettingsFile() {
    String fileName = getSettingPath() + "/Settings.xlsx";
    return fileName;
  }

  /**
   * Get the resutls file name
   * @return the results file name
   */
  public String getResultsFile() {
    String barcode = instrumentExecutionService.getParticipantID();
    String fileName = getResultPath() + "/Results-" + barcode + ".xlsx";
    return fileName;
  }

  /**
   * Get the locale
   * @return the locale
   */
  public Locale getLocale() {
    return locale;
  }

  /**
   * Set the locale
   * @param locale the locale
   */
  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  public void setCdttResourceBundle(ResourceBundle cdttResourceBundle) {
    this.cdttResourceBundle = cdttResourceBundle;
  }

  /**
   *  Backup the existing settings file
   */
  private void createBackupSettingsFile() {
    File file = new File( getSettingsFile() );
    File backupFile = new File( file.getAbsoluteFile() + ".orig" );
    try {
      if( file.exists() && !file.isDirectory() ) {
        log.info("backing up existing settings file");
        FileUtil.copyFile( file, backupFile );
      }
    } catch( Exception e ) {
      throw new RuntimeException("Error backing up CDTT " + file.getAbsoluteFile() + " file", e);
    }
  }

  /**
   *  Restore the settings file from its backup
   */
  private void restoreSettingsFile() {
    File file = new File( getSettingsFile() );
    File backupFile = new File( file.getAbsoluteFile() + ".orig" );
    try {
      if(backupFile.exists()) {
        log.info( "restoring pre-existing settings file {} => {}",
          backupFile.getAbsoluteFile(), file.getAbsoluteFile());
        FileUtil.copyFile(backupFile,file);
        backupFile.delete();
      }
    } catch( Exception e ) {
      throw new RuntimeException("Error restoring CDTT " + file.getAbsoluteFile() + " file", e);
    }
  }
}
