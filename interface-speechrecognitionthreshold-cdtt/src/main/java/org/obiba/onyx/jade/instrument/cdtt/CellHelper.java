package org.obiba.onyx.jade.instrument.cdtt;

public class CellHelper {

  public enum Action {
    VALIDATE,
    WRITE,
    READ
  }

  public CellHelper(Integer direction, String data, Action type) {
    this.direction = direction;
    this.data = data;
    this.type = type;
  }

  public void setData(String data) { this.data = data; }
  public void setDirection(Integer direction) { this.direction = direction; }
  public void setType(Action type) { this.type = type; }

  public String getData() { return this.data; }
  public Integer getDirection() { return this.direction; }
  public Action getType() { return this.type; }

  public Integer getDx() {
    Integer dx = 0;
    switch (this.direction) {
      case 1: dx = 1; break;
      case 2: dx = 0; break;
      case 3: dx = -1; break;
      case 4: dx = 0; break;
      default: dx = 0; break;
    }
    return dx;
  }

  public Integer getDy() {
    Integer dy = 0;
    switch (this.direction) {
      case 1: dy = 0; break;
      case 2: dy = 1; break;
      case 3: dy = 0; break;
      case 4: dy = -1; break;
      default: dy = 0; break;
    }
    return dy;
  }

  private String data;
  private Integer direction;
  private Action type;
}
