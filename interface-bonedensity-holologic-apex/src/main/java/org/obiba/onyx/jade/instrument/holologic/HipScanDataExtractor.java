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
import org.obiba.onyx.util.data.DataBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hip (left or right) data are extracted from Hip and HipHSA PatScan db tables.
 */
public class HipScanDataExtractor extends APEXScanDataExtractor {

  private Side side;

  /**
   * @param patScanDb
   * @param refCurveDb
   * @param participantData
   * @param side
   * @param server
   * @param apexReceiver
   */
  protected HipScanDataExtractor(JdbcTemplate patScanDb, JdbcTemplate refCurveDb, Map<String, String> participantData, Side side, DicomServer server, ApexReceiver apexReceiver) {
    super(patScanDb, refCurveDb, participantData, server, apexReceiver);
    this.side = side;
    ApexDicomData dicom1 = new ApexDicomData();
    dicom1.validator.put(Tag.Modality, new TagEntry(true,true,"OT"));
    dicom1.validator.put(Tag.BodyPartExamined, new TagEntry(true,true,"HIP"));
    dicom1.validator.put(Tag.ImageAndFluoroscopyAreaDoseProduct, new TagEntry(true,false,""));
    dicom1.validator.put(Tag.PatientOrientation, new TagEntry(true,false,""));
    dicom1.validator.put(Tag.BitsAllocated, new TagEntry(true,true,"8"));
    if(null != side) {
      dicom1.validator.put(Tag.Laterality, new TagEntry(true,true,(Side.LEFT == side ? "L" : "R")));
    } else {
      dicom1.validator.put(Tag.Laterality, new TagEntry(true,false,""));
    }
    dicom1.validator.put(Tag.PhotometricInterpretation, new TagEntry(true,true,"RGB"));
    dicom1.validator.put(Tag.PixelSpacing, new TagEntry(true,true,null));
    dicom1.validator.put(Tag.SamplesPerPixel, new TagEntry(true,true,"3"));
    dicom1.validator.put(Tag.MediaStorageSOPClassUID, new TagEntry(true,true,UID.SecondaryCaptureImageStorage));
    dicom1.name = getResultPrefix() + "_DICOM";
    apexDicomList.add(dicom1);
  }

  @Override
  public String getName() {
    switch(side) {
    case LEFT:
      return "L_HIP";
    default:
      return "R_HIP";
    }
  }

  @Override
  public String getBodyPartName() {
    return "HIP";
  }

  @Override
  protected void extractDataImpl(Map<String, Data> data) {
    data.put(getResultPrefix() + "_SIDE", DataBuilder.buildText(side.toString()));
    extractScanData("Hip", data, new HipResultSetExtractor(data));
    extractScanData("HipHSA", data, new HipHSAResultSetExtractor(data));
  }

  @Override
  protected long getScanType() {
    switch(side) {
    case LEFT:
      return 2l;
    default:
      return 3l;
    }
  }

  @Override
  public String getRefType() {
    return "H";
  }

  @Override
  public String getRefSource() {
    return "NHANES";
  }

  private final class HipResultSetExtractor extends ResultSetDataExtractor {

    public HipResultSetExtractor(Map<String, Data> data) {
      super(data);
    }

    @Override
    protected void putData() throws SQLException, DataAccessException {
      putDouble("TROCH_AREA");
      putDouble("TROCH_BMC");
      putDouble("TROCH_BMD");
      putDouble("INTER_AREA");
      putDouble("INTER_BMC");
      putDouble("INTER_BMD");
      putDouble("NECK_AREA");
      putDouble("NECK_BMC");
      putDouble("NECK_BMD");
      putDouble("WARDS_AREA");
      putDouble("WARDS_BMC");
      putDouble("WARDS_BMD");
      putDouble("HTOT_AREA");
      putDouble("HTOT_BMC");
      putDouble("HTOT_BMD");
      putLong("ROI_TYPE");
      putDouble("ROI_WIDTH");
      putDouble("ROI_HEIGHT");
      putDouble("AXIS_LENGTH");
      putString("PHYSICIAN_COMMENT");
    }

  }

  private final class HipHSAResultSetExtractor extends ResultSetDataExtractor {

    public HipHSAResultSetExtractor(Map<String, Data> data) {
      super(data);
    }

    @Override
    protected void putData() throws SQLException, DataAccessException {
      putDouble("NN_BMD");
      putDouble("NN_CSA");
      putDouble("NN_CSMI");
      putDouble("NN_WIDTH");
      putDouble("NN_ED");
      putDouble("NN_ACT");
      putDouble("NN_PCD");
      putDouble("NN_CMP");
      putDouble("NN_SECT_MOD");
      putDouble("NN_BR");
      putDouble("IT_BMD");
      putDouble("IT_CSA");
      putDouble("IT_CSMI");
      putDouble("IT_WIDTH");
      putDouble("IT_ED");
      putDouble("IT_ACT");
      putDouble("IT_PCD");
      putDouble("IT_CMP");
      putDouble("IT_SECT_MOD");
      putDouble("IT_BR");
      putDouble("FS_BMD");
      putDouble("FS_CSA");
      putDouble("FS_CSMI");
      putDouble("FS_WIDTH");
      putDouble("FS_ED");
      putDouble("FS_ACT");
      putDouble("FS_PCD");
      putDouble("FS_CMP");
      putDouble("FS_SECT_MOD");
      putDouble("FS_BR");
      putDouble("SHAFT_NECK_ANGLE");
    }
  }

  public Side getSide() {
    return side;
  }

}
