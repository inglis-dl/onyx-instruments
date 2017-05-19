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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.tool.dcmrcv.DicomServer;
import org.obiba.onyx.jade.instrument.holologic.APEXInstrumentRunner.Side;
import org.obiba.onyx.util.data.Data;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Whole Body data are extracted from:
 * Wbody, WbodyComposition, SubRegionBone, SubRegionComposition, ObesityIndices, AndroidGynoidComposition
 * PatScan db tables.
 */
public class WholeBodyScanDataExtractor extends APEXScanDataExtractor {

  /**
   * @param patScanDb
   * @param refCurveDb
   * @param participantData
   * @param server
   * @param apexReceiver
   */
  protected WholeBodyScanDataExtractor(JdbcTemplate patScanDb, JdbcTemplate refCurveDb, Map<String, String> participantData, DicomServer server, ApexReceiver apexReceiver) {
    super(patScanDb, refCurveDb, participantData, server, apexReceiver);
    ApexDicomData dicom1 = new ApexDicomData();
    dicom1.validator.put(Tag.Modality, new TagEntry(true,true,"OT"));
    dicom1.validator.put(Tag.BodyPartExamined, new TagEntry(true,true,null));
    dicom1.validator.put(Tag.ImageAndFluoroscopyAreaDoseProduct, new TagEntry(true,false,""));
    dicom1.validator.put(Tag.PatientOrientation, new TagEntry(true,false,""));
    dicom1.validator.put(Tag.BitsAllocated, new TagEntry(true,true,"8"));
    dicom1.validator.put(Tag.PhotometricInterpretation, new TagEntry(true,true,"RGB"));
    dicom1.validator.put(Tag.PixelSpacing, new TagEntry(true,true,null));
    dicom1.validator.put(Tag.SamplesPerPixel, new TagEntry(true,true,"3"));
    dicom1.validator.put(Tag.MediaStorageSOPClassUID, new TagEntry(true,true,UID.SecondaryCaptureImageStorage));
    dicom1.name = getResultPrefix() + "_DICOM_1";
    apexDicomList.add(dicom1);

    ApexDicomData dicom2 = new ApexDicomData();
    dicom2.validator.put(Tag.Modality, new TagEntry(true,true,"OT"));
    dicom2.validator.put(Tag.BodyPartExamined, new TagEntry(true,true,null));
    dicom2.validator.put(Tag.ImageAndFluoroscopyAreaDoseProduct, new TagEntry());
    dicom2.validator.put(Tag.PatientOrientation, new TagEntry(true,false,""));
    dicom2.validator.put(Tag.BitsAllocated, new TagEntry(true,true,"8"));
    dicom2.validator.put(Tag.PhotometricInterpretation, new TagEntry(true,true,"RGB"));
    dicom2.validator.put(Tag.PixelSpacing, new TagEntry(true,true,null));
    dicom2.validator.put(Tag.SamplesPerPixel, new TagEntry(true,true,"3"));
    dicom2.validator.put(Tag.MediaStorageSOPClassUID, new TagEntry(true,true,UID.SecondaryCaptureImageStorage));
    dicom2.name = getResultPrefix() + "_DICOM_2";
    apexDicomList.add(dicom2);
  }

  @Override
  public String getName() {
    return "WB";
  }

  @Override
  public String getBodyPartName() {
    return "WBODY";
  }

  @Override
  protected long getScanType() {
    return 5l;
  }

  @Override
  public String getRefType() {
    return "W";
  }

  @Override
  public String getRefSource() {
    return "NHANES";
  }

  @Override
  protected void extractDataImpl(Map<String, Data> data) {
    extractScanData("Wbody", data, new WbodyResultSetExtractor(data));
    extractScanData("WbodyComposition", data, new WbodyCompositionResultSetExtractor(data));
    extractScanData("SubRegionBone", data, new SubRegionBoneResultSetExtractor(data));
    extractScanData("SubRegionComposition", data, new SubRegionCompositionResultSetExtractor(data));
    extractScanData("ObesityIndices", data, new ObesityIndicesResultSetExtractor(data));
    extractScanData("AndroidGynoidComposition", data, new AndroidGynoidCompositionResultSetExtractor(data));
  }

  private final class WbodyResultSetExtractor extends ResultSetDataExtractor {

    public WbodyResultSetExtractor(Map<String, Data> data) {
      super(data);
    }

    @Override
    protected void putData() throws SQLException, DataAccessException {
      putDouble("WBTOT_AREA");
      putDouble("WBTOT_BMC");
      putDouble("WBTOT_BMD");
      putDouble("SUBTOT_AREA");
      putDouble("SUBTOT_BMC");
      putDouble("SUBTOT_BMD");
      putDouble("HEAD_AREA");
      putDouble("HEAD_BMC");
      putDouble("HEAD_BMD");
      putDouble("LARM_AREA");
      putDouble("LARM_BMC");
      putDouble("LARM_BMD");
      putDouble("RARM_AREA");
      putDouble("RARM_BMC");
      putDouble("RARM_BMD");
      putDouble("LRIB_AREA");
      putDouble("LRIB_BMC");
      putDouble("LRIB_BMD");
      putDouble("RRIB_AREA");
      putDouble("RRIB_BMC");
      putDouble("RRIB_BMD");
      putDouble("T_S_AREA");
      putDouble("T_S_BMC");
      putDouble("T_S_BMD");
      putDouble("L_S_AREA");
      putDouble("L_S_BMC");
      putDouble("L_S_BMD");
      putDouble("PELV_AREA");
      putDouble("PELV_BMC");
      putDouble("PELV_BMD");
      putDouble("LLEG_AREA");
      putDouble("LLEG_BMC");
      putDouble("LLEG_BMD");
      putDouble("RLEG_AREA");
      putDouble("RLEG_BMC");
      putDouble("RLEG_BMD");
      putString("PHYSICIAN_COMMENT");
    }

    @Override
    protected String getVariableName(String name) {
      if(name.equals("PHYSICIAN_COMMENT")) {
        return super.getVariableName("WB_" + name);
      }
      return super.getVariableName(name);
    }
  }

  private final class WbodyCompositionResultSetExtractor extends ResultSetDataExtractor {

    public WbodyCompositionResultSetExtractor(Map<String, Data> data) {
      super(data);
    }

    @Override
    protected void putData() throws SQLException, DataAccessException {
      putDouble("FAT_STD");
      putDouble("LEAN_STD");
      putDouble("BRAIN_FAT");
      putDouble("WATER_LBM");
      putDouble("HEAD_FAT");
      putDouble("HEAD_LEAN");
      putDouble("HEAD_MASS");
      putDouble("HEAD_PFAT");
      putDouble("LARM_FAT");
      putDouble("LARM_LEAN");
      putDouble("LARM_MASS");
      putDouble("LARM_PFAT");
      putDouble("RARM_FAT");
      putDouble("RARM_LEAN");
      putDouble("RARM_MASS");
      putDouble("RARM_PFAT");
      putDouble("TRUNK_FAT");
      putDouble("TRUNK_LEAN");
      putDouble("TRUNK_MASS");
      putDouble("TRUNK_PFAT");
      putDouble("L_LEG_FAT");
      putDouble("L_LEG_LEAN");
      putDouble("L_LEG_MASS");
      putDouble("L_LEG_PFAT");
      putDouble("R_LEG_FAT");
      putDouble("R_LEG_LEAN");
      putDouble("R_LEG_MASS");
      putDouble("R_LEG_PFAT");
      putDouble("SUBTOT_FAT");
      putDouble("SUBTOT_LEAN");
      putDouble("SUBTOT_MASS");
      putDouble("SUBTOT_PFAT");
      putDouble("WBTOT_FAT");
      putDouble("WBTOT_LEAN");
      putDouble("WBTOT_MASS");
      putDouble("WBTOT_PFAT");
      putString("PHYSICIAN_COMMENT");
    }

    @Override
    protected String getVariableName(String name) {
      if(name.equals("PHYSICIAN_COMMENT") || name.equals("FAT_STD") || name.equals("LEAN_STD") || name.equals("BRAIN_FAT") || name.equals("WATER_LBM")) {
        return super.getVariableName("WBC_" + name);
      }
      return super.getVariableName(name);
    }
  }

  private final class SubRegionBoneResultSetExtractor extends ResultSetDataExtractor {

    public SubRegionBoneResultSetExtractor(Map<String, Data> data) {
      super(data);
    }

    @Override
    protected void putData() throws SQLException, DataAccessException {
      putDouble("NET_AVG_AREA");
      putDouble("NET_AVG_BMC");
      putDouble("NET_AVG_BMD");
      putDouble("GLOBAL_AREA");
      putDouble("GLOBAL_BMC");
      putDouble("GLOBAL_BMD");

      putLong("NO_REGIONS");

      for(int i = 1; i <= 14; i++) {
        putString("REG" + i + "_NAME");
        putDouble("REG" + i + "_AREA");
        putDouble("REG" + i + "_BMC");
        putDouble("REG" + i + "_BMD");
      }

      putString("PHYSICIAN_COMMENT");
    }

    @Override
    protected String getVariableName(String name) {
      if(name.equals("PHYSICIAN_COMMENT") || name.equals("NO_REGIONS") || name.startsWith("REG")) {
        return super.getVariableName("SRB_" + name);
      }
      return super.getVariableName(name);
    }
  }

  private final class SubRegionCompositionResultSetExtractor extends ResultSetDataExtractor {

    public SubRegionCompositionResultSetExtractor(Map<String, Data> data) {
      super(data);
    }

    @Override
    protected void putData() throws SQLException, DataAccessException {
      putDouble("NET_AVG_FAT");
      putDouble("NET_AVG_LEAN");
      putDouble("NET_AVG_MASS");
      putDouble("NET_AVG_PFAT");
      putDouble("GLOBAL_FAT");
      putDouble("GLOBAL_LEAN");
      putDouble("GLOBAL_MASS");
      putDouble("GLOBAL_PFAT");

      putLong("NO_REGIONS");

      for(int i = 1; i <= 14; i++) {
        putString("REG" + i + "_NAME");
        putDouble("REG" + i + "_FAT");
        putDouble("REG" + i + "_LEAN");
        putDouble("REG" + i + "_MASS");
        putDouble("REG" + i + "_PFAT");
      }

      putInt("TISSUE_ANALYSIS_METHOD");

      putString("PHYSICIAN_COMMENT");
    }

    @Override
    protected String getVariableName(String name) {
      if(name.equals("PHYSICIAN_COMMENT") || name.equals("NO_REGIONS") || name.startsWith("REG")) {
        return super.getVariableName("SRC_" + name);
      }
      return super.getVariableName(name);
    }
  }

  private final class ObesityIndicesResultSetExtractor extends ResultSetDataExtractor {

    public ObesityIndicesResultSetExtractor(Map<String, Data> data) {
      super(data);
    }

    @Override
    protected void putData() throws SQLException, DataAccessException {
      putDouble("FAT_STD");
      putDouble("LEAN_STD");
      putDouble("BRAIN_FAT");
      putDouble("WATER_LBM");
      putDouble("TOTAL_PERCENT_FAT");
      putDouble("BODY_MASS_INDEX");
      putDouble("ANDROID_GYNOID_RATIO");
      putDouble("ANDROID_PERCENT_FAT");
      putDouble("GYNOID_PERCENT_FAT");
      putDouble("FAT_MASS_RATIO");
      putDouble("TRUNK_LIMB_FAT_MASS_RATIO");
      putDouble("FAT_MASS_HEIGHT_SQUARED");
      putDouble("TOTAL_FAT_MASS");
      putDouble("LEAN_MASS_HEIGHT_SQUARED");
      putDouble("APPENDAGE_LEAN_MASS_HEIGHT_2");
      putDouble("TOTAL_LEAN_MASS");
      putString("PHYSICIAN_COMMENT");
    }

    @Override
    protected String getVariableName(String name) {
      if(name.equals("PHYSICIAN_COMMENT") || name.equals("FAT_STD") || name.equals("LEAN_STD") || name.equals("BRAIN_FAT") || name.equals("WATER_LBM")) {
        return super.getVariableName("OI_" + name);
      }
      return super.getVariableName(name);
    }

  }

  private final class AndroidGynoidCompositionResultSetExtractor extends ResultSetDataExtractor {

    public AndroidGynoidCompositionResultSetExtractor(Map<String, Data> data) {
      super(data);
    }

    @Override
    protected void putData() throws SQLException, DataAccessException {
      putDouble("ANDROID_FAT");
      putDouble("ANDROID_LEAN");
      putDouble("GYNOID_FAT");
      putDouble("GYNOID_LEAN");
      putString("PHYSICIAN_COMMENT");
    }

    @Override
    protected String getVariableName(String name) {
      if(name.equals("PHYSICIAN_COMMENT")) {
        return super.getVariableName("AGC_" + name);
      }
      return super.getVariableName(name);
    }
  }

  @Override
  public Side getSide() {
    return null;
  }

}
