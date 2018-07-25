package org.obiba.onyx.jade.instrument.cdtt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.net.URISyntaxException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.obiba.onyx.jade.instrument.ExternalAppLauncherHelper;
import org.obiba.onyx.jade.instrument.service.InstrumentExecutionService;
import org.obiba.onyx.util.FileUtil;
import org.obiba.onyx.util.data.Data;
import org.obiba.onyx.util.data.DataBuilder;

import org.springframework.beans.factory.InitializingBean;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

public class CdttInstrumentRunnerTest {

  private ExternalAppLauncherHelper externalAppHelper;

  private CdttInstrumentRunner cdttInstrumentRunner;

  private InstrumentExecutionService instrumentExecutionServiceMock;

  private static String SETTING_FILENAME = "Settings.xlsx";

  private static String TEMPLATE_FILENAME = "Results-Template.xlsx";

  private static String RESULT_FILENAME = "Results-12345678.xlsx";

  private String errorKey = null;

  private Set<String> errorDescriptions = null;

  @Before
  public void setUp() {
    cdttInstrumentRunner = new CdttInstrumentRunner() {
      @Override
      String warningPopup(String key) {
        setErrorKey(key);
        return key;
      }
    };

    // Create a test directory to simulate software installation path.
    File softwareInstallPathSimulated = new File(System.getProperty("java.io.tmpdir"),"test-cdtt");
    if (!softwareInstallPathSimulated.exists() && !softwareInstallPathSimulated.mkdirs()) {
      throw new UnknownError("Cannot create exe directory at path: " + softwareInstallPathSimulated.getAbsolutePath());
    }
    cdttInstrumentRunner.setSoftwareInstallPath(softwareInstallPathSimulated.getAbsolutePath());

    File resultPathSimulated = new File(softwareInstallPathSimulated, "Results");
    if (!resultPathSimulated.exists() && !resultPathSimulated.mkdirs()) {
      throw new UnknownError("Cannot create result directory at path: " + resultPathSimulated.getAbsolutePath());
    }
    cdttInstrumentRunner.setResultPath(resultPathSimulated.getAbsolutePath());

    File settingPathSimulated = new File(softwareInstallPathSimulated, "Settings");
    if (!settingPathSimulated.exists() && !settingPathSimulated.mkdirs()) {
      throw new UnknownError("Cannot create setting directory at path: " + settingPathSimulated.getAbsolutePath());
    }
    cdttInstrumentRunner.setSettingPath(settingPathSimulated.getAbsolutePath());

    // Cannot mock ExternalAppLauncherHelper (without EasyMock extension!),
    // so for now, use the class itself with the launch method overridden to
    // do nothing.
    externalAppHelper = new ExternalAppLauncherHelper() {
      @Override
      public void launch() {
        // do nothing
      }

      @Override
      public boolean isSotfwareAlreadyStarted(String lockname) {
        return false;
      }
    };

    externalAppHelper.setWorkDir(softwareInstallPathSimulated.getAbsolutePath());
    cdttInstrumentRunner.setExternalAppHelper(externalAppHelper);

    // Create a mock instrumentExecutionService for testing.
    instrumentExecutionServiceMock = createMock(InstrumentExecutionService.class);
    expect(instrumentExecutionServiceMock.getParticipantID()).andReturn("12345678").anyTimes();
    cdttInstrumentRunner.setLocale(Locale.CANADA);
    cdttInstrumentRunner.setInstrumentExecutionService(instrumentExecutionServiceMock);

    try {
      simulateSettings();
    } catch( Exception e ) {
      e.printStackTrace();
    }
  }

  @After
  public void tearDown() {
    cdttInstrumentRunner.shutdown();
    String softwareInstallPathSimulatedPath = new File(System.getProperty("java.io.tmpdir"),"test-cdtt").getPath();
    File softwareInstallPathSimulated = new File(softwareInstallPathSimulatedPath);
    if (softwareInstallPathSimulated.exists()) {
      try {
        FileUtil.delete(softwareInstallPathSimulated);
      } catch(IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Test
  public void testInitialize() {
    // Set arbitrary inputs for testing.
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_GENDER")).andReturn(DataBuilder.buildText("M"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_LANGUAGE")).andReturn(DataBuilder.buildText("ENGLISH"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_CDTT_TEST_EAR")).andReturn(DataBuilder.buildInteger(2));
    replay(instrumentExecutionServiceMock);

    cdttInstrumentRunner.initialize();

    verify(instrumentExecutionServiceMock);

    verifyInitialization();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRun() /*throws FileNotFoundException, IOException, URISyntaxException*/ {
    externalAppHelper.launch();

    // Replay the input values: instrument runner requires them to validate results file content.
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_GENDER")).andReturn(DataBuilder.buildText("M"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_LANGUAGE")).andReturn(DataBuilder.buildText("ENGLISH"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_CDTT_TEST_EAR")).andReturn(DataBuilder.buildInteger(2));
    instrumentExecutionServiceMock.addOutputParameterValues((Map<String, Data>)anyObject());
    replay(instrumentExecutionServiceMock);

    try {
      simulateResults();
    } catch( Exception e ) {
      e.printStackTrace();
    }

    // Compare the values read with the ones from the result file.
    Map<String, Data> results = cdttInstrumentRunner.retrieveDeviceData();

    Assert.assertEquals("2018-07-25 10:49:09", results.get("RES_TEST_DATETIME").getValueAsString());
    Assert.assertEquals("EN_CA", results.get("RES_TEST_LANGUAGE").getValueAsString());
    Assert.assertEquals("Male", results.get("RES_TEST_TALKER").getValueAsString());
    Assert.assertEquals("Binaural", results.get("RES_TEST_EAR").getValueAsString());
    Assert.assertEquals(-7.619047619047619, Double.parseDouble(results.get("RES_SRT").getValueAsString()), 0);
    Assert.assertEquals(1.6271505915615334, Double.parseDouble(results.get("RES_STD_DEV").getValueAsString()), 0);
    Assert.assertEquals(14, Double.parseDouble(results.get("RES_REV_NB").getValueAsString()), 0);

    // Make sure that the results are sent to the server.
    cdttInstrumentRunner.sendDataToServer(results);

    verify(instrumentExecutionServiceMock);

    Assert.assertTrue(null == getErrorKey());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRunEmptyResultFile() throws FileNotFoundException, IOException {
    // Create empty Result data file.
    FileOutputStream output = new FileOutputStream(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME));
    output.close();

    externalAppHelper.launch();

    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_GENDER")).andReturn(DataBuilder.buildText("M"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_LANGUAGE")).andReturn(DataBuilder.buildText("ENGLISH"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_CDTT_TEST_EAR")).andReturn(DataBuilder.buildInteger(2));
    instrumentExecutionServiceMock.addOutputParameterValues((Map<String, Data>)anyObject());
    replay(instrumentExecutionServiceMock);

    Map<String, Data> results = cdttInstrumentRunner.retrieveDeviceData();

    Assert.assertTrue(getErrorKey().equals("problemResultFile"));
    Assert.assertTrue(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
    setErrorKey(null);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRunNoResultFile() /*throws Exception*/ {
    externalAppHelper.launch();

    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_GENDER")).andReturn(DataBuilder.buildText("M"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_LANGUAGE")).andReturn(DataBuilder.buildText("ENGLISH"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_CDTT_TEST_EAR")).andReturn(DataBuilder.buildInteger(2));
    instrumentExecutionServiceMock.addOutputParameterValues((Map<String, Data>)anyObject());
    replay(instrumentExecutionServiceMock);

    Map<String, Data> results = cdttInstrumentRunner.retrieveDeviceData();

    Assert.assertTrue(getErrorKey().equals("noResultFileFound"));
    Assert.assertFalse(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
    setErrorKey(null);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRunParseResultFile() /*throws Exception*/ {
    externalAppHelper.launch();

    try {
      simulateResults();
    } catch( Exception e ) {
      e.printStackTrace();
    }

    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_GENDER")).andReturn(DataBuilder.buildText("M"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_PARTICIPANT_LANGUAGE")).andReturn(DataBuilder.buildText("FRENCH"));
    expect(instrumentExecutionServiceMock.getInputParameterValue("INPUT_CDTT_TEST_EAR")).andReturn(DataBuilder.buildInteger(2));
    instrumentExecutionServiceMock.addOutputParameterValues((Map<String, Data>)anyObject());
    replay(instrumentExecutionServiceMock);

    Map<String, Data> results = cdttInstrumentRunner.retrieveDeviceData();

    Assert.assertTrue(getErrorKey().equals("problemResultFile"));
    Assert.assertTrue(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
    setErrorKey(null);
  }


  @Test
  public void testShutdown() /*throws FileNotFoundException, IOException, URISyntaxException, InterruptedException*/ {
    try {
      simulateResults();
    } catch( Exception e ) {
      e.printStackTrace();
    }

    cdttInstrumentRunner.shutdown();

    verifyCleanup();
  }

  private void verifyInitialization() {
    // Make sure the backup settings file has been created.
    Assert.assertTrue(new File(cdttInstrumentRunner.getSettingPath(), SETTING_FILENAME + ".orig").exists());
    Assert.assertTrue(new File(cdttInstrumentRunner.getSettingPath(), SETTING_FILENAME ).exists());
    Assert.assertTrue(new File(cdttInstrumentRunner.getResultPath(), TEMPLATE_FILENAME ).exists());
    Assert.assertFalse(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME ).exists());

    // Make sure the results path has only the template.I
    File path = new File(cdttInstrumentRunner.getResultPath());
    Assert.assertTrue( 1 == path.list().length );
  }

  private void verifyCleanup() {
    // Make sure the backup file has been deleted.
    Assert.assertFalse(new File(cdttInstrumentRunner.getSettingPath(), SETTING_FILENAME + ".orig").exists());
    Assert.assertTrue(new File(cdttInstrumentRunner.getSettingPath(), SETTING_FILENAME ).exists());
    Assert.assertTrue(new File(cdttInstrumentRunner.getResultPath(), TEMPLATE_FILENAME ).exists());
    Assert.assertFalse(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME ).exists());

    // Make sure the results path has only the template.
    File path = new File(cdttInstrumentRunner.getResultPath());
    Assert.assertTrue( 1 == path.list().length );
  }

  private void simulateResults() throws IOException, URISyntaxException {
    // Copy results file.
    FileUtil.copyFile(new File(getClass().getResource("/" + RESULT_FILENAME).toURI()), new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME));
  }

  private void simulateSettings() throws IOException, URISyntaxException {
    // Copy settings file.
    FileUtil.copyFile(new File(getClass().getResource("/" + SETTING_FILENAME).toURI()), new File(cdttInstrumentRunner.getSettingPath(), SETTING_FILENAME));
    // Copy template file.
    FileUtil.copyFile(new File(getClass().getResource("/" + TEMPLATE_FILENAME).toURI()), new File(cdttInstrumentRunner.getResultPath(), TEMPLATE_FILENAME));
  }

  public String getErrorKey() {
    return errorKey;
  }

  public void setErrorKey(String errorKey) {
    this.errorKey = errorKey;
  }
}
