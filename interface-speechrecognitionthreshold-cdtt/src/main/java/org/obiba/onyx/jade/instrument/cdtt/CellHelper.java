package org.obiba.onyx.jade.instrument.cdtt;

/**
 *  A helper class for navigating, reading, validating and writing Excel workbook cells
 */
public class CellHelper {

  public enum Action {
    VALIDATE,
    WRITE,
    READ
  }

  /**
   *  Constructor
   * @param direction compass point direction from a label cell to its data
   * @param data content to compare with when validating or to write with
   * @param type enumerated action
   */
  public CellHelper(int direction, String data, Action type) {
    this.direction = direction;
    this.data = data;
    this.type = type;
  }

  /**
   * Set the data to compare with or to write with
   * @param data content to compare with when validating or to write with
   */
  public void setData(String data) { this.data = data; }

  /**
   * Set the compass point direction (1 = east, 2 = south, 3 = west, 4 = north)
   * @param direction compass point direction from a label cell to its data
   */
  public void setDirection(int direction) { this.direction = direction; }

  /**
   * Set the action to perform on the cell (validate, write or read)
   * @param type enumerated action
   */
  public void setType(Action type) { this.type = type; }

  /**
   * Get the data
   * @return the data
   */
  public String getData() { return this.data; }

  /**
   * Get the direction
   * @return the compass point direction
   */
  public int getDirection() { return this.direction; }

  /**
   * Get the action
   * @return the action
   */
  public Action getType() { return this.type; }

  /**
   * Get the column offset based on compass point direction
   * @return the column offset
   */
  public int getDx() {
    int dx = 0;
    switch (this.direction) {
      case 1: dx = 1; break;
      case 2: dx = 0; break;
      case 3: dx = -1; break;
      case 4: dx = 0; break;
      default: dx = 0; break;
    }
    return dx;
  }

  /**
   * Get the row offset based on compass point direction
   * @return the row offset
   */
  public int getDy() {
    int dy = 0;
    switch (this.direction) {
      case 1: dy = 0; break;
      case 2: dy = 1; break;
      case 3: dy = 0; break;
      case 4: dy = -1; break;
      default: dy = 0; break;
    }
    return dy;
  }

  /**
   * Cell data for writing or validating
   */
  private String data;

  /**
   * Compass point direction for navigation
   */
  private int direction;

  /**
   * Enumerated action to perform
   */
  private Action type;
}
