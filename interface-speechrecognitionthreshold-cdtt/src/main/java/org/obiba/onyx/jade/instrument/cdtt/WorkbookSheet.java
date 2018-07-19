package org.obiba.onyx.jade.instrument.cdtt;

import org.obiba.onyx.jade.instrument.cdtt.CellHelper;
import org.obiba.onyx.jade.instrument.cdtt.CellHelper.Action;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import org.obiba.onyx.util.data.Data;
import org.obiba.onyx.util.data.DataBuilder;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class WorkbookSheet {

  public WorkbookSheet(String fileName, String sheetName) {
    this.fileName = fileName;
    this.sheetName = sheetName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getFileName() {
    return this.fileName;
  }

  public void setSheetName(String sheetName) {
    this.sheetName = sheetName;
  }

  public String getSheetName() {
    return this.sheetName;
  }

  public boolean canRead() {
    File file = new File(this.fileName);
    return (file.exists() && !file.isDirectory());
  }

  public void writeSheet(HashMap<String,CellHelper> map) throws IOException {

    File file = new File(this.fileName);

    if(file.exists() && !file.isDirectory()) {

      Set<Map.Entry<String,CellHelper>> set = map.entrySet();
      FileInputStream stream = new FileInputStream(file);
      XSSFSheet sheet = workbook.getSheet(this.sheetName);

      for(Map.Entry<String,CellHelper> entry : set) {

	String key = entry.getKey().getData().getValueAsString();
	boolean found = false;
	Iterator<Row> rowIterator = sheet.iterator();

	while (rowIterator.hasNext() && !found) {
	  Row row = rowIterator.next();
	  Iterator<Cell> cellIterator = row.cellIterator();

	  while (cellIterator.hasNext() && !found) {
	    Cell cell = cellIterator.next();

	    if(cell.getStringCellValue().equals(key)) {
	      found = true;
	      int idx = cell.getRowIndex();
	      int idy = cell.getColumnIndex();
	      CellHelper h = entry.getValue();
	      cell = sheet.getRow(idx+h.getDx()).getCell(idy+h.getDy());

	      Data value;
	      switch (cell.getCellType()) {
		case Cell.CELL_TYPE_BOOLEAN:
		  value.setValue(cell.getBooleanCellValue());
		  break;
		case Cell.CELL_TYPE_NUMERIC:
		  value.setValue(cell.getNumericCellValue());
		  break;
		case Cell.CELL_TYPE_DATE:
		  value.setValue(cell.getDateCellValue());
		  break;
		case Cell.CELL_TYPE_STRING:
		  value.setValue(cell.getStringCellValue());
		  break;
		case default:
		  value.setValue(cell.getStringCellValue());
		  break;
	      }

	      if( h.getType() == CellHelperType.VALIDATE )
	      {
		if(0 != value.compareTo(h.getData())) {
		  throw new IOException;
		}
	      } else if( h.getType() == CellHelperType.WRITE ) {
	        Data output = h.getData();
		switch (cell.getCellType()) {
		  case Cell.CELL_TYPE_BOOLEAN:
		    cell.setCellValue(output.getValue());
		    break;
		  case Cell.CELL_TYPE_NUMERIC:
		    cell.setCellValue(output.getValue());
		    break;
		  case Cell.CELL_TYPE_DATE:
		    cell.setCellValue(output.getValue());
		    break;
		  case Cell.CELL_TYPE_STRING:
		    cell.setCellValue(output.getValueAsString());
		    break;
		  default:
		    cell.setCellValue(output.getValueAsString());
		    break;
		}
	      }
	    }
	  } // end while
	} // end while
      } // end for

      try {
        FileOutputStream outstream = new FileOutputStream(file);
        workbook.write(outstream);
      }
      workbook.close();

    } // end if
  }

  public Map<String, Data> readSheet(HashMap<String,CellHelper> map) throws IOException {
    Map<String, Data> outputData = new HashMap<String, Data>();

    File file = new File(this.fileName);

    if(file.exists() && !file.isDirectory()) {

      Set<Map.Entry<String,CellHelper>> set = map.entrySet();
      FileInputStream stream = new FileInputStream(file);
      XSSFSheet sheet = workbook.getSheet(this.sheetName);

      for(Map.Entry<String,CellHelper> entry : set) {

	String key = entry.getKey().getData().getValueAsString();
	boolean found = false;
	Iterator<Row> rowIterator = sheet.iterator();

	while (rowIterator.hasNext() && !found) {
	  Row row = rowIterator.next();
	  Iterator<Cell> cellIterator = row.cellIterator();

	  while (cellIterator.hasNext() && !found) {
	    Cell cell = cellIterator.next();

	    if(cell.getStringCellValue().equals(key)) {
	      found = true;
	      int idx = cell.getRowIndex();
	      int idy = cell.getColumnIndex();
	      CellHelper h = entry.getValue();
	      cell = sheet.getRow(idx+h.getDx()).getCell(idy+h.getDy());

	      Data value;
	      switch (cell.getCellType()) {
		case Cell.CELL_TYPE_BOOLEAN:
		  data.setValue(cell.getBooleanCellValue());
		  break;
		case Cell.CELL_TYPE_NUMERIC:
		  data.setValue(cell.getNumericCellValue());
		  break;
		case Cell.CELL_TYPE_DATE:
		  data.setValue(cell.getDateCellValue());
		  break;
		case Cell.CELL_TYPE_STRING:
		  data.setValue(cell.getStringCellValue());
		  break;
                default:
		  data.setValue(cell.getStringCellValue());
		  break;
	      }

	      if( h.getType() == CellHelperType.VALIDATE )
	      {
		if(0 != value.compareTo(h.getData())) {
		  throw new IOException;
		}
	      } else if( h.getType() == CellHelperType.READ ) {
		String outputName = h.getData().getValueAsString();
	        outputData.put(outputName, value);
	      }
	    }
	  } // end while
	} // end while
      } // end for
    } // end if

    return outputData;
  }

  private String fileName;
  private String sheetName;
}
