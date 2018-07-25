package org.obiba.onyx.jade.instrument.cdtt;

import org.obiba.onyx.jade.instrument.cdtt.CellHelper;
import org.obiba.onyx.jade.instrument.cdtt.CellHelper.Action;

import org.obiba.onyx.util.data.Data;
import org.obiba.onyx.util.data.DataType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;

/**
 * A class for reading and writing Excel workbook sheets
 */
public class WorkbookSheet {

  private static final SimpleDateFormat DATE_FORMAT =  new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss");

  /**
   * Create class instance from file name and Excel workbook sheet name
   * @param fileName the Excel file name
   * @param sheetName the Excel workbook sheet name
   */
  public WorkbookSheet(String fileName, String sheetName) {
    this.fileName = fileName;
    this.sheetName = sheetName;
  }

  /**
   * Set the file name
   * @param fileName the Excel file name
   */
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /**
   * Get the file name
   * @return the Excel file name
   */
  public String getFileName() {
    return this.fileName;
  }

  /**
   * Set the workbook sheet name
   * @param sheetName the Excel workbook sheet name
   */
  public void setSheetName(String sheetName) {
    this.sheetName = sheetName;
  }

  /**
   * Get the workbook sheet name
   * @return the Excel workbook sheet name
   */
  public String getSheetName() {
    return this.sheetName;
  }

  /**
   * Can the file be read?
   * @return whether the file can be read
   */
  public boolean canRead() {
    File file = new File(this.fileName);
    return (file.exists() && !file.isDirectory());
  }

  /**
   * Helper function to convert a Cell's value to String
   * @param cell a worksheet cell
   * @return the cell content as a String
   */
  public static String cellToString(Cell cell) {
    String value = "";
    if( null != cell ) {
      switch ( cell.getCellTypeEnum() ) {
        case BOOLEAN:
          value = String.valueOf(cell.getBooleanCellValue());
          break;
        case NUMERIC:
          value = String.valueOf(cell.getNumericCellValue());
          break;
        case STRING:
          value = cell.getStringCellValue();
          break;
        case FORMULA:
          value = cell.getCellFormula();
          break;
        case ERROR:
          value = String.valueOf(cell.getErrorCellValue());
          break;
        default:
          value = "";
          break;
      }
    }
    return value;
  }

  /**
   * Determine if a string represents a datetime in format
   * yyyy-MM-dd, HH:mm:ss
   * @param value the string to check
   * @return true if the string represents a datetime
   */
  public static boolean isDatetime(String value) {
    boolean result = true;
    try {
      DATE_FORMAT.parse(value);
    } catch( ParseException e ) {
      result = false;
    }
    return result;
  }

  /**
   * Write into the Excel file with a map of cell labels to identify
   * which cells to write into.
   * @param map a map of cell labels and associated data
   * @throws IOException
   * @throws ParseException
   * @see CellHelper
   */
  public void write( HashMap<String, CellHelper> map ) throws IOException, ParseException {
    File file = new File(this.fileName);

    if( canRead() ) {
      Set<Map.Entry<String,CellHelper>> set = map.entrySet();
      FileInputStream stream = new FileInputStream(file);
      XSSFWorkbook workbook = null;
      try {
        workbook = new XSSFWorkbook(stream);
      } catch( NotOfficeXmlFileException e ) {
        throw new IOException( e.getMessage() );
      }
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

	    if( CellType.STRING == cell.getCellTypeEnum() && cell.getStringCellValue().equals(key) ) {
	      found = true;
	      int idy = cell.getRowIndex();
	      int idx = cell.getColumnIndex();
	      CellHelper h = entry.getValue();
	      cell = sheet.getRow( idy + h.getDy() ).getCell( idx + h.getDx() );
	      String value = cellToString(cell);
	      String output = h.getData();

	      if( h.getType() == Action.VALIDATE )
	      {

		if( 0 != value.compareTo(output) ) {
                  workbook.close();
                  String message = "failed to validate " + value + " != " + output;
		  throw new IOException(message);
		}
	      } else if( h.getType() == Action.WRITE ) {
		switch( cell.getCellTypeEnum() ) {
		  case BOOLEAN:
		    cell.setCellValue(Boolean.parseBoolean(output));
		    break;
		  case NUMERIC:
                    if( value.contains(".") ) {
                      cell.setCellValue(Double.parseDouble(output));
                    } else if( isDatetime(value) ) {
                      try {
                        cell.setCellValue(DATE_FORMAT.parse(output));
                      } catch( ParseException e ) {
                        workbook.close();
		        throw e;
                      }
                    } else {
                      cell.setCellValue(Integer.parseInt(output));
                    }
		    break;
		  case STRING:
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

      FileOutputStream outstream = null;
      IOException processException = null;
      try {
        outstream = new FileOutputStream(file);
        workbook.write(outstream);
      } catch(IOException e) {
        processException = e;
      } finally {
        try {
          if( null != outstream )
            outstream.close();
        } catch (IOException e) {
          processException = e;
        }
        workbook.close();
        if(null != processException)
          throw processException;
      }
    } else {
      throw new IOException();
    }
  }

  /**
   * Read from the Excel file with a map of cell labels to identify
   * which cells to read from and / or validate.
   * @param map a map of cell labels and associated data
   * @return map a map of data labels and Data values
   * @throws IOException
   * @throws ParseException
   * @see CellHelper
   */
  public Map<String, Data> read( HashMap<String, CellHelper> map ) throws IOException, ParseException {
    Map<String, Data> outputData = new HashMap<String, Data>();
    File file = new File(this.fileName);

    if( canRead() ) {
      Set<Map.Entry<String,CellHelper>> set = map.entrySet();
      FileInputStream stream = new FileInputStream(file);
      XSSFWorkbook workbook = null;
      try {
        workbook = new XSSFWorkbook(stream);
      } catch( NotOfficeXmlFileException e ) {
        throw new IOException( e.getMessage() );
      }

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

	    if( CellType.STRING == cell.getCellTypeEnum() && cell.getStringCellValue().equals(key) ) {
	      found = true;
	      int idy = cell.getRowIndex();
	      int idx = cell.getColumnIndex();
	      CellHelper h = entry.getValue();
	      cell = sheet.getRow( idy + h.getDy() ).getCell( idx + h.getDx() );

	      String value = cellToString(cell);
	      String output = h.getData();

	      if( h.getType() == Action.VALIDATE )
	      {
		if( 0 != value.compareTo(output) ) {
                  workbook.close();
                  String message = "failed to validate " + value + " != " + output;
		  throw new IOException(message);
		}
	      } else if( h.getType() == Action.READ ) {
                switch( cell.getCellTypeEnum() ) {
                  case BOOLEAN:
	            outputData.put(output, new Data(DataType.BOOLEAN, Boolean.parseBoolean(value)));
                    break;
                  case NUMERIC:
                    if( value.contains(".") ) {
	              outputData.put(output, new Data( DataType.DECIMAL,
                        Double.parseDouble(value)));
                    } else if( isDatetime(value) ) {
                      try {
	                outputData.put(output, new Data(DataType.DATE,
                          DATE_FORMAT.parse(value)));
                      } catch( ParseException e ) {
                        workbook.close();
		        throw e;
                      }
                    } else {
	              outputData.put(output, new Data( DataType.DECIMAL,
                        Integer.parseInt(value)));
                    }
                    break;
                  case STRING:
                    if( isDatetime(value) ) {
                      try {
                        outputData.put(output, new Data(DataType.DATE,
                          DATE_FORMAT.parse(value)));
                      } catch ( ParseException e ) {
                        workbook.close();
                        throw e;
                      }
                    } else {
	              outputData.put(output, new Data(DataType.TEXT, value));
                    }
                    break;
                  default:
	            outputData.put(output, new Data(DataType.TEXT, value));
                    break;
                }
	      }
	    }
	  } // end while
	} // end while
      } // end for
      workbook.close();
    } else {
      throw new IOException();
    }

    return outputData;
  }

  /**
   * The Excel file name
   */
  private String fileName;

  /**
   * The Excel workbook sheet name
   */
  private String sheetName;
}
