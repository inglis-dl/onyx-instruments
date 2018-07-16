package org.obiba.onyx.jade.instrument.cdtt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.obiba.onyx.jade.instrument.ExternalAppLauncherHelper;
import org.obiba.onyx.jade.instrument.cdtt.CdttInstrumentRunner.LineCallback;
import org.obiba.onyx.jade.instrument.service.InstrumentExecutionService;
import org.obiba.onyx.util.FileUtil;
import org.obiba.onyx.util.data.Data;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

public class CdttInstrumentRunnerTest {

  private ExternalAppLauncherHelper externalAppHelper;

  private CdttInstrumentRunner cdttInstrumentRunner;

  private InstrumentExecutionService instrumentExecutionServiceMock;

  private static String RESULT_FILENAME = "Cdtt_ONYX_result.csv";

  private String errorKey = null;

  private Set<String> errorDescriptions = null;

  @Before
  public void setUp() throws URISyntaxException {

    cdttInstrumentRunner = new CdttInstrumentRunner() {
      @Override
      String warningPopup(String key, String[] errSet) {
        setErrorKey(key);
        if(errSet != null) setErrorDescriptions(new HashSet<String>(Arrays.asList(errSet)));
        // super.warningPopup(key, errSet);
        return key;
      }

      @Override
      String warningPopup(String key) {
        return warningPopup(key, null);
      }
    };

    // Create a test directory to simulate Noddle Test software installation path.
    File cdttSoftSimulated = new File(System.getProperty("java.io.tmpdir"),"test-cdtt");
    if (!cdttSoftSimulated.exists() && !cdttSoftSimulated.mkdirs()) {
      throw new UnknownError("Cannot create directory at path: " + cdttSoftSimulated.getAbsolutePath());
    }
    cdttInstrumentRunner.setSoftwareInstallPath(cdttSoftSimulated.getAbsolutePath());

    // Noddle Test result path.
    File resultPath = new File(cdttSoftSimulated, "RESULT");
    if (!resultPath.exists() && !resultPath.mkdirs()) {
      throw new UnknownError("Cannot create directory at path: " + resultPath.getAbsolutePath());
    }
    cdttInstrumentRunner.setResultPath(resultPath.getAbsolutePath());

    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("ct-instrument");
    cdttInstrumentRunner.setResourceBundleMessageSource(messageSource);

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

    cdttInstrumentRunner.setExternalAppHelper(externalAppHelper);

    // Create a mock instrumentExecutionService for testing.
    instrumentExecutionServiceMock = createMock(InstrumentExecutionService.class);
    cdttInstrumentRunner.setInstrumentExecutionService(instrumentExecutionServiceMock);

    cdttInstrumentRunner.setLocale(Locale.CANADA);

    cdttInstrumentRunner.setPackagePrefix("Cdtt");
    cdttInstrumentRunner.initializeEndDataCodeMap();
  }

  @After
  public void tearDown() {
    cdttInstrumentRunner.shutdown();
    String cdttSoftSimulatedPath = new File(System.getProperty("java.io.tmpdir"),"test-cdtt").getPath();
    File cdttSoftSimulated = new File(cdttSoftSimulatedPath);
    if (cdttSoftSimulated.exists()) {
      try {
        FileUtil.delete(cdttSoftSimulated);
      } catch(IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Ignore
  @Test
  public void testInitializeWithoutFiles() throws IOException, URISyntaxException {
    cdttInstrumentRunner.initialize();
    cdttInstrumentRunner.releaseConfigFileAndResultFileLock();

    // Verify that the Noddle Test result file has been deleted successfully.
    Assert.assertFalse(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
  }

  @Test
  public void testInitializeWithFiles() throws IOException, URISyntaxException {
    simulateResultsAndInput(RESULT_FILENAME);
    cdttInstrumentRunner.initialize();

    // Verify that the Noddle Test result file has been deleted successfully.
    Assert.assertFalse(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
  }

  @Test
  public void testRunNoResultFile() throws Exception {
    // nothing happens: we should find data files kept as after initialize step
    externalAppHelper.launch();
    cdttInstrumentRunner.getDataFiles();

    // Verify that the Noddle Test result file has been deleted successfully.
    Assert.assertFalse(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
  }

  @Test
  public void testRunEmptyResultFile() throws Exception {
    // Create empty Result data file.
    FileOutputStream output = new FileOutputStream(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME));
    output.close();

    externalAppHelper.launch();
    cdttInstrumentRunner.getDataFiles();

    Assert.assertTrue(getErrorKey().equals("noTestKey"));
    Assert.assertTrue(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
    setErrorKey(null);
  }

  @Test
  public void testRunMissingResultFile() throws Exception {
    simulateResultsAndInput("Cdtt_ONYX_resultMiss.csv");

    externalAppHelper.launch();
    cdttInstrumentRunner.getDataFiles();

    Assert.assertTrue(getErrorKey().equals("missingTestKey"));
    Assert.assertTrue(isContainedInErrorDescription("Reasoning Quiz"));

    // Assert.assertTrue(getErrorDescSet().contains("Working Memory"));
    Assert.assertTrue(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
    setErrorKey(null);
    setErrorDescriptions(null);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testRunNormalResultFile() throws Exception {
    simulateResultsAndInput(RESULT_FILENAME);
    instrumentExecutionServiceMock.addOutputParameterValues((Map<String, Data>) EasyMock.anyObject());

    replay(instrumentExecutionServiceMock);
    externalAppHelper.launch();
    cdttInstrumentRunner.getDataFiles();
    verify(instrumentExecutionServiceMock);

    Assert.assertTrue(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
    Assert.assertTrue(getErrorKey() == null);
    Assert.assertTrue(getErrorDescSet().isEmpty());
  }

  @Ignore
  @Test
  public void testRunTooMuchResultFile() throws Exception {
    // Create a second dummy Result data file.
    FileOutputStream output = new FileOutputStream(new File(cdttInstrumentRunner.getResultPath(), "Cdtt_ONYX_result_1.csv"));
    output.write((byte) 234432141);
    output.close();

    externalAppHelper.launch();
    cdttInstrumentRunner.getDataFiles();

    // Nothing happens: the two files are kept and a message in log appears
    Assert.assertTrue(new File(cdttInstrumentRunner.getResultPath()).listFiles().length == 2);
  }

  @Test
  public void testShutdown() throws FileNotFoundException, IOException, URISyntaxException, InterruptedException {
    simulateResultsAndInput(RESULT_FILENAME);
    cdttInstrumentRunner.shutdown();

    Assert.assertFalse(new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME).exists());
  }

  @Ignore
  @Test(expected = CdttInstrumentRunnerException.class)
  public void testSoftwareInstallPathDoesNotExist() throws CdttInstrumentRunnerException {
    String nonExistentSoftwareInstallPath = new File("target", "non-existent-software-install-directory").getPath();
    cdttInstrumentRunner.setSoftwareInstallPath(nonExistentSoftwareInstallPath);
    cdttInstrumentRunner.initializeCdttInstrumentRunner();
  }

  @Test
  public void testParsingUtf16LeBomConfigFile() throws URISyntaxException {
    File utf16ConfigFile = new File(getClass().getResource("/Config-UTF-16LE-BOM.txt").toURI());

    HashSet<String> configuredTests = cdttInstrumentRunner.extractTestsFromResultFile(utf16ConfigFile, new LineCallback() {
      public String handleLine(String line) {
        if(line.startsWith("!") == false) return (line.substring(0, 2));
        return null;
      }
    });
    Assert.assertEquals("Expected 5 tests in the config file.", 5, configuredTests.size());
  }

  @Test
  public void testParsingIso8859ConfigFile() throws URISyntaxException {
    File iso8859ConfigFile = new File(getClass().getResource("/Config-iso-8859-1.txt").toURI());

    HashSet<String> configuredTests = cdttInstrumentRunner.extractTestsFromResultFile(iso8859ConfigFile, new LineCallback() {
      public String handleLine(String line) {
        if(line.startsWith("!") == false) return (line.substring(0, 2));
        return null;
      }
    });
    Assert.assertEquals("Expected 3 tests in the config file.", 3, configuredTests.size());
  }

  @Test
  public void testConvertingStringCodesToCompletedTests() {
    cdttInstrumentRunner.initializeEndDataCodeMap();
    Set<String> input = new HashSet<String>();
    input.add("22"); // PA end data code.
    input.add("31"); // RQ data code.
    Set<Cdtts> completedTests = cdttInstrumentRunner.getTestsCompleted(input);
    Assert.assertTrue(completedTests.contains(Cdtts.PA));
    Assert.assertEquals(1, completedTests.size());
  }

  @Test(expected = NumberFormatException.class)
  public void testConvertingStringCodesToCompletedTestsWithNonIntegerInput() {
    Set<String> input = new HashSet<String>();
    input.add("22"); // PA end data code.
    input.add("RQCodeIsNotAnInteger");
    cdttInstrumentRunner.getTestsCompleted(input);
  }

  private void simulateResultsAndInput(String fileToCopy) throws FileNotFoundException, IOException, URISyntaxException {
    // Copy Result data file.
    FileUtil.copyFile(new File(getClass().getResource("/" + fileToCopy).toURI()), new File(cdttInstrumentRunner.getResultPath(), RESULT_FILENAME));

    // Copy Config file.
    FileUtil.copyFile(new File(getClass().getResource("/Config.txt").toURI()), new File(cdttInstrumentRunner.getSoftwareInstallPath(), "Config.txt"));
  }

  private void verifyFileContent(File file, TestLineCallback callback) {
    InputStream fileStrm = null;
    InputStreamReader strmReader = null;
    LineNumberReader reader = null;

    try {
      fileStrm = new FileInputStream(file);
      strmReader = new InputStreamReader(fileStrm);
      reader = new LineNumberReader(strmReader);

      callback.handleLine(reader);

      fileStrm.close();
      strmReader.close();
      reader.close();
    } catch(Exception ex) {
      throw new RuntimeException("Error: retrieve cognitive test data", ex);
    }
  }

  private interface TestLineCallback {
    public void handleLine(LineNumberReader reader) throws IOException;
  }

  public String getErrorKey() {
    return errorKey;
  }

  public void setErrorKey(String errorKey) {
    this.errorKey = errorKey;
  }

  public Set<String> getErrorDescSet() {
    return errorDescriptions != null ? errorDescriptions : Collections.<String> emptySet();
  }

  public void setErrorDescriptions(HashSet<String> errorDescriptions) {
    this.errorDescriptions = errorDescriptions;
  }

  private boolean isContainedInErrorDescription(String lookingFor) {
    for(String error : getErrorDescSet()) {
      if(error.indexOf(lookingFor) > 0) return true;
    }
    return false;
  }

}
