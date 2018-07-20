package org.obiba.onyx.jade.instrument.cdtt;

import org.obiba.onyx.jade.instrument.cdtt.CellHelper;
import org.obiba.onyx.jade.instrument.cdtt.CellHelper.Action;

import org.obiba.onyx.util.data.Data;
import org.obiba.onyx.util.data.DataType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.text.SimpleDateFormat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.lang.String;
import java.util.Date;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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

  public void write( HashMap<String, CellHelper> map ) throws IOException {

    File file = new File(this.fileName);

    if( file.exists() && !file.isDirectory() ) {

      Set<Map.Entry<String,CellHelper>> set = map.entrySet();
      FileInputStream stream = new FileInputStream(file);
      XSSFWorkbook workbook = new XSSFWorkbook(stream);
      XSSFSheet sheet = workbook.getSheet(this.sheetName);

      for( Map.Entry<String,CellHelper> entry : set ) {

	String key = entry.getKey();
	boolean found = false;
	Iterator<Row> rowIterator = sheet.iterator();

	while( rowIterator.hasNext() && !found ) {
	  Row row = rowIterator.next();
	  Iterator<Cell> cellIterator = row.cellIterator();

	  while( cellIterator.hasNext() && !found ) {
	    Cell cell = cellIterator.next();

	    if( cell.getStringCellValue().equals(key) ) {
	      found = true;
	      int idx = cell.getRowIndex();
	      int idy = cell.getColumnIndex();
	      CellHelper h = entry.getValue();
	      cell = sheet.getRow( idx + h.getDx() ).getCell( idy + h.getDy() );

	      String value = cell.getStringCellValue();

	      if( h.getType() == Action.VALIDATE )
	      {
		if( 0 != value.compareTo(h.getData()) ) {
		  throw new IOException();
		}
	      } else if( h.getType() == Action.WRITE ) {
	        String output = h.getData();
		switch ( cell.getCellType() ) {
		  case Cell.CELL_TYPE_BOOLEAN:
		    cell.setCellValue(Boolean.parseBoolean(output));
		    break;
		  case Cell.CELL_TYPE_NUMERIC:
                    if(value.contains(".")) {
                      cell.setCellValue(Double.parseDouble(output));
                    } else if (value.contains(":") && value.contains("-")) {
                      cell.setCellValue(new SimpleDateFormat("yyyy-MM-dd, hh:mm:ss").parse(output));
                    } else {
                      cell.setCellValue(Integer.parseInt(output));
                    }
		    cell.setCellValue(Double.parseDouble(output));
		    break;
		  case Cell.CELL_TYPE_STRING:
		    cell.setCellValue(output);
		    break;
		  default:
		    cell.setCellValue(output);
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
      } catch(Exception ex) {
        throw ex;
      }
      workbook.close();

    } // end if
  }

  public Map<String, Data> read( HashMap<String, CellHelper> map ) throws IOException {
    Map<String, Data> outputData = new HashMap<String, Data>();

    File file = new File(this.fileName);

    if( file.exists() && !file.isDirectory() ) {

      Set<Map.Entry<String,CellHelper>> set = map.entrySet();
      FileInputStream stream = new FileInputStream(file);
      XSSFWorkbook workbook = new XSSFWorkbook(stream);
      XSSFSheet sheet = workbook.getSheet(this.sheetName);

      for( Map.Entry<String,CellHelper> entry : set ) {

	String key = entry.getKey();
	boolean found = false;
	Iterator<Row> rowIterator = sheet.iterator();

	while( rowIterator.hasNext() && !found ) {
	  Row row = rowIterator.next();
	  Iterator<Cell> cellIterator = row.cellIterator();

	  while( cellIterator.hasNext() && !found ) {
	    Cell cell = cellIterator.next();

	    if( cell.getStringCellValue().equals(key) ) {
	      found = true;
	      int idx = cell.getRowIndex();
	      int idy = cell.getColumnIndex();
	      CellHelper h = entry.getValue();
	      cell = sheet.getRow( idx + h.getDx() ).getCell( idy + h.getDy() );

	      String value = cell.getStringCellValue();

	      if( h.getType() == Action.VALIDATE )
	      {
		if( 0 != value.compareTo(h.getData()) ) {
		  throw new IOException();
		}
	      } else if( h.getType() == Action.READ ) {
		String outputName = h.getData();
                switch (cell.getCellType()) {
                  case Cell.CELL_TYPE_BOOLEAN:
	            outputData.put(outputName, new Data(DataType.BOOLEAN, Boolean.parseBoolean(value)));
                    break;
                  case Cell.CELL_TYPE_NUMERIC:
                    if(value.contains(".")) {
	              outputData.put(outputName, new Data( DataType.DECIMAL,
                        Double.parseDouble(value)));
                    } else if (value.contains(":") && value.contains("-")) {
	              outputData.put(outputName, new Data(DataType.DATE,
                        new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(value)));
                    } else {
	              outputData.put(outputName, new Data( DataType.DECIMAL,
                        Integer.parseInt(value)));
                    }
                    break;
                  case Cell.CELL_TYPE_STRING:
	            outputData.put(outputName, new Data(DataType.TEXT, value));
                    break;
                  default:
	            outputData.put(outputName, new Data(DataType.TEXT, value));
                    break;
                }
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
