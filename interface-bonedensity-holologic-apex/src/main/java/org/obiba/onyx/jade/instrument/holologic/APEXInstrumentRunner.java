/*******************************************************************************
 * Copyright (c) 2011 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.jade.instrument.holologic;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.dcm4che2.tool.dcmrcv.DicomServer;
import org.obiba.onyx.jade.instrument.InstrumentRunner;
import org.obiba.onyx.jade.instrument.holologic.IVAImagingScanDataExtractor.Energy;
import org.obiba.onyx.jade.instrument.service.InstrumentExecutionService;
import org.obiba.onyx.util.data.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileSystemUtils;

public class APEXInstrumentRunner implements InstrumentRunner {

  private static final Logger log = LoggerFactory.getLogger(APEXInstrumentRunner.class);

  protected InstrumentExecutionService instrumentExecutionService;

  private JdbcTemplate patScanDb;

  private JdbcTemplate refCurveDb;

  private DicomSettings dicomSettings;

  private DicomServer server;

  private File dcmDir;

  private Set<String> outVendorNames;

  private Locale locale;

  private List<String> sentVariables = new ArrayList<String>();

  private ApexReceiver apexReceiver = new ApexReceiver();

  private String participantID;

  private Map<String, String> participantData = new HashMap<String, String>();

  private boolean isRepeatable;

  private static final String DICOM = "DICOM";

  public enum Side {
    LEFT, RIGHT
  }

  /**
   * Implements initialize() of parent InstrumentRunner. Delete results from previous measurement and initiate the input
   * file to be read by the external application.
   */
  public void initialize() {
    participantID = instrumentExecutionService.getParticipantID();
    isRepeatable = instrumentExecutionService.isRepeatableMeasure();
    initApexReceiverStatus();
    outVendorNames = instrumentExecutionService.getExpectedOutputParameterVendorNames();

    try {
      File tmpDir = File.createTempFile("dcm", "");
      if(false == tmpDir.delete() || false == tmpDir.mkdir()) {
        throw new RuntimeException("Cannot create temp directory");
      }
      dcmDir = tmpDir;
      log.info("DICOM files stored to {}", dcmDir.getAbsolutePath());
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
    server = new DicomServer(dcmDir, dicomSettings);
  }

  /**
   * Implements run() of parent InstrumentRunner. Launch the external application, retrieve and send the data.
   */
  public void run() {
    log.info("Start Dicom server");
    try {
      server.start();
    } catch(IOException e) {
      log.error("Error starting Dicom server: " + e);
    }
    apexReceiver.waitForExit();
  }

  /**
   * Implements shutdown() of parent InstrumentRunner. Closes dicom communication channel, deletes temporary dcm files
   * transferred by DICOM transfer from Apex sender to dcm4che receiver.
   */
  public void shutdown() {
    log.info("Shutdown Dicom server");
    server.stop();
    deleteTemporaryDicomFiles();
  }

  /**
   * Called by initialize(). Initialize and display the GUI for capturing the dcm files from Apex.
   */
  public void initApexReceiverStatus() {
    apexReceiver.setParticipantID(participantID);
    apexReceiver.setCaptureActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        retrieveMeasurements();

        List<String> missing = getMissingVariables();
        boolean completeVariable = true;
        boolean completeDicom = true;
        if(false == missing.isEmpty()) {
          int dicomCount = 0;
          for(String out : missing) {
            if(-1 != out.indexOf(DICOM)) {
              dicomCount++;
            }
          }
          completeVariable = dicomCount == missing.size();
          completeDicom = 0 == dicomCount;
        }

        if(completeVariable) {  // all variables have been captured
          if(isRepeatable && 1 < instrumentExecutionService.getExpectedMeasureCount()) {
            apexReceiver.setVariableStatusOKPending();
          } else {
            apexReceiver.setVariableStatusOK();
          }
        } else {
          apexReceiver.setVariableStatusNotOK();
        }
        if(completeDicom) {  // all dicom files have been correctly captured
          apexReceiver.setDicomStatusOK();
        } else {
          if(!apexReceiver.isValidPandRDicomFile() ||
             !apexReceiver.isValidParticipantDicomFile()) {
            apexReceiver.setDicomStatusNotOK();
          } else { // no dicom files sent for verification
            apexReceiver.setDicomStatusNotReady();
          }
        }

        if(completeVariable && completeDicom) {
          apexReceiver.setSaveEnable();
        }

        apexReceiver.repaint();
      }
    });
    apexReceiver.setVisible(true);
  }

  /**
   * Called by initApexReceiverStatus(). Retrieve the device data, add to list of variables to send, send to
   * instrumentExecutionService.
   */
  private void retrieveMeasurements() {
    log.info("Retrieving measurements");
    List<Map<String, Data>> dataList = retrieveDeviceData();
    log.info("Sending data to server");
    sentVariables.clear();
    for(Map<String, Data> dataMap : dataList) {
      for(Map.Entry<String, Data> entry : dataMap.entrySet()) {
        sentVariables.add(entry.getKey());
      }
      // send only if the measure is complete (all variables assigned)
      // because repeatable measures accept partial variable sets
      if(isRepeatable) {
        if(outVendorNames.equals(dataMap.keySet())) {
          sendDataToServer(dataMap);
        }
      } else {
        sendDataToServer(dataMap);
      }
    }
  }

  /**
   * Called by initApexReceiverStatus(). Return list of missing variables.
   *
   * @return
  */
  private List<String> getMissingVariables() {
    List<String> missing = new ArrayList<String>();
    List<String> sentVariablesCopy = new ArrayList<String>(sentVariables);

    // if this is a repeatable measure, check if all variables in the measure have been sent
    for(int i = 0; i < instrumentExecutionService.getExpectedMeasureCount(); i++) {
      for(String out : outVendorNames) {
        if(false == sentVariablesCopy.contains(out)) {
          missing.add(out);
        } else {
          sentVariablesCopy.remove(out);
        }
      }
    }
    if(false == missing.isEmpty()) {
      log.info("Missing variables: " + missing);
    }
    return missing;
  }

  /**
   * Called by retrieveMeasurements(). Queries Apex PatScanDb for patient key, DOB, gender based on participant visit
   * ID. Extracts Hip, Forearm, Whole Body and Spine scans and analysis data.
   *
   * @return
   */
  private List<Map<String, Data>> retrieveDeviceData() {

    List<Map<String, Data>> dataList = new ArrayList<Map<String, Data>>();

    log.info("participantID: " + participantID);

    participantData.clear();

    String sql = "SELECT PATIENT_KEY, BIRTHDATE, SEX, ETHNICITY FROM PATIENT WHERE IDENTIFIER1 = ?";
    try {
      Map<String, Object> results = patScanDb.queryForMap(sql, new Object[] { participantID });
      if(null != results) {
        participantData.put("participantID", participantID);
        participantData.put("participantKey", results.get("PATIENT_KEY").toString());
        participantData.put("participantDOB", results.get("BIRTHDATE").toString());
        participantData.put("participantGender", results.get("SEX").toString());
        String ethnicity = "W";
        if(results.containsKey("ETHNICITY") && null != results.get("ETHNICITY")) {
          ethnicity = results.get("ETHNICITY").toString();
        }
        participantData.put("participantEthnicity", ethnicity);
      }
    } catch(DataAccessException e) {
      log.info("Cannot find the requested participant in Apex: " + sql );
      throw e;
    }

    log.info("hip block in runner start");
    if(instrumentExecutionService.hasInputParameter("HipSide")) {
      String hipSide = instrumentExecutionService.getInputParameterValue("HipSide").getValue();
      log.info("hipSide: " + hipSide);
      log.info("expected: " + instrumentExecutionService.getExpectedMeasureCount());
      if(null != hipSide) {
        if(hipSide.toUpperCase().startsWith("L")) {
          extractLeftHip(dataList);
        } else if(hipSide.toUpperCase().startsWith("R")) {
          extractRightHip(dataList);
        } else if(hipSide.toUpperCase().startsWith("B")) {
          if(instrumentExecutionService.getExpectedMeasureCount() > 1) {
            extractLeftHip(dataList);
            extractRightHip(dataList);
          } else {
            extractScanData(dataList, new HipScanDataExtractor(patScanDb, refCurveDb, participantData, Side.LEFT, server, apexReceiver));
            extractScanData(dataList, new HipScanDataExtractor(patScanDb, refCurveDb, participantData, Side.RIGHT, server, apexReceiver));
          }
        }
      }
    } else if(instrumentExecutionService.getExpectedMeasureCount() > 1) {
      extractLeftHip(dataList);
      extractRightHip(dataList);
    } else {
      extractScanData(dataList, new HipScanDataExtractor(patScanDb, refCurveDb, participantData, Side.LEFT, server, apexReceiver));
      extractScanData(dataList, new HipScanDataExtractor(patScanDb, refCurveDb, participantData, Side.RIGHT, server, apexReceiver));
    }
    log.info("hip block in runner end");

    log.info("forearm block in runner start");
    if(instrumentExecutionService.hasInputParameter("ForearmSide")) {
      String forearmSide = instrumentExecutionService.getInputParameterValue("ForearmSide").getValue();
      if(null != forearmSide) {
        if(forearmSide.toUpperCase().startsWith("L")) {
          extractScanData(dataList, new ForearmScanDataExtractor(patScanDb, refCurveDb, participantData, Side.LEFT, server, apexReceiver) {
            @Override
            public String getName() {
              return "FA";
            }
          });
        } else if(forearmSide.toUpperCase().startsWith("R")) {
          extractScanData(dataList, new ForearmScanDataExtractor(patScanDb, refCurveDb, participantData, Side.RIGHT, server, apexReceiver) {
            @Override
            public String getName() {
              return "FA";
            }
          });
        }
      }
    } else {
      extractScanData(dataList, new ForearmScanDataExtractor(patScanDb, refCurveDb, participantData, Side.LEFT, server, apexReceiver));
      extractScanData(dataList, new ForearmScanDataExtractor(patScanDb, refCurveDb, participantData, Side.RIGHT, server, apexReceiver));
    }
    log.info("forearm block in runner end");

    log.info("wbody block in runner start");
    extractScanData(dataList, new WholeBodyScanDataExtractor(patScanDb, refCurveDb, participantData, server, apexReceiver));
    log.info("wbody block in runner end");

    log.info("iva spine block in runner start");
    extractScanData(dataList, new IVAImagingScanDataExtractor(patScanDb, refCurveDb, participantData, Energy.CLSA_DXA, server, apexReceiver));
    log.info("iva spine block in runner end");

    log.info("ap spine block in runner start");
    extractScanData(dataList, new APSpineScanDataExtractor(patScanDb, refCurveDb, participantData, server, apexReceiver));
    log.info("ap spine block in runner end");

    return dataList;
  }

  /**
   * Called by retrieveMeasurements().
   *
   * @param data
   */
  public void sendDataToServer(Map<String, Data> data) {
    instrumentExecutionService.addOutputParameterValues(data);
  }

  /**
   * Called by retrieveDeviceData(). Generic calling interface to extract Apex data. Passes abstract data extractor:
   * child classes unique to scan type (ie., forearm, spine etc.).
   *
   * @param dataList
   * @param extractor
   */
  private void extractScanData(List<Map<String, Data>> dataList, APEXScanDataExtractor extractor) {
    log.info("extractScanData");
    // filter the values to output
    Map<String, Data> extractedData = extractor.extractData();
    Map<String, Data> outputData = new HashMap<String, Data>();

    for(Entry<String, Data> entry : extractedData.entrySet()) {
      if(outVendorNames.contains(entry.getKey())) {
        outputData.put(entry.getKey(), entry.getValue());
      }
    }
    log.info(extractedData + "");
    log.info(outputData + "");
    dataList.add(outputData);
  }

  /**
   * Called by retrieveDeviceData(). Calling interface to extract Apex right hip data.
   *
   * @param dataList
   */
  private void extractRightHip(List<Map<String, Data>> dataList) {
    extractScanData(dataList, new HipScanDataExtractor(patScanDb, refCurveDb, participantData, Side.RIGHT, server, apexReceiver) {
      @Override
      public String getName() {
        return "HIP";
      }
    });
  }

  /**
   * Called by retrieveDeviceData(). Calling interface to extract Apex left hip data.
   *
   * @param dataList
   */
  private void extractLeftHip(List<Map<String, Data>> dataList) {
    extractScanData(dataList, new HipScanDataExtractor(patScanDb, refCurveDb, participantData, Side.LEFT, server, apexReceiver) {
      @Override
      public String getName() {
        return "HIP";
      }
    });
  }

  /**
   * Called by shutdown(). Deletes all dcm files transferred from Apex to client as well as parent folder.
   */
  private void deleteTemporaryDicomFiles() {
    log.info("Delete temporary dicom files");
    FileSystemUtils.deleteRecursively(dcmDir);
  }

  //
  // Set/Get methods.
  //

  public void setInstrumentExecutionService(InstrumentExecutionService instrumentExecutionService) {
    this.instrumentExecutionService = instrumentExecutionService;
  }

  public void setPatScanDb(JdbcTemplate patScanDb) {
    this.patScanDb = patScanDb;
  }

  public void setRefCurveDb(JdbcTemplate refCurveDb) {
    this.refCurveDb = refCurveDb;
  }

  public void setDicomSettings(DicomSettings dicomSettings) {
    this.dicomSettings = dicomSettings;
  }

  public Locale getLocale() {
    return locale;
  }

  public void setLocale(Locale locale) {
    this.locale = locale;
  }

}
