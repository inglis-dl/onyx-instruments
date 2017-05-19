package org.obiba.onyx.jade.instrument.ge.vivid;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.tool.dcmrcv.DicomServer;
import org.dcm4che2.tool.dcmrcv.DicomServer.State;
import org.dcm4che2.tool.dcmrcv.DicomServer.StateListener;
import org.dcm4che2.tool.dcmrcv.DicomServer.StorageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DicomStorageScp {

  protected Logger log = LoggerFactory.getLogger(DicomStorageScp.class);

  private final CountDownLatch exitLatch = new CountDownLatch(1);

  private final DicomServer server;

  private final DicomStoragePredicate dicomStoragePredicate;

  private JFrame frmDicomServer;

  private JTextField aeTitle;

  private JComboBox hostname;

  private JSpinner port;

  private JButton startStop;

  private JTable table;

  private DefaultTableModel model;

  private static final String PATIENT_ID = "Patient ID";

  public static final String STUDYINSTANCEUID = "Study Instance UID";

  private static final String NUMBER = "File Number";

  public static final String LATERALITY = "Laterality";

  public static final List<String> columns = new ArrayList<String>();

  static {
    columns.add("#");
    columns.add(PATIENT_ID);
    columns.add(STUDYINSTANCEUID);
    columns.add(NUMBER);
    columns.add(LATERALITY);
  }

  /**
   * Create the application.
   */
  public DicomStorageScp(DicomServer server) {
    this(server, null);
  }

  public DicomStorageScp(DicomServer server, DicomStoragePredicate dicomStoragePredicate) {
    if(server == null) throw new IllegalArgumentException();
    this.server = server;
    this.dicomStoragePredicate = dicomStoragePredicate;

    this.server.addStorageListener(new DicomStorageListener());

    this.server.addStateListener(new StateListener() {

      @Override
      public void onStateChange(State newState) {
        if(newState == State.STARTED) {
          startStop.setText("Stop");
        } else {
          startStop.setText("Start");
        }

        boolean editable = newState == State.STOPPED;
        aeTitle.setEditable(editable);
        hostname.setEditable(editable);
        port.setEnabled(editable);
      }
    });
    initialize();
    bind();
  }


  public void show() {
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        try {
          frmDicomServer.setVisible(true);
        } catch(Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public void waitForExit() {
    // Block on the lock (held by the Event queue thread)
    try {
      exitLatch.await();
    } catch(InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Initialize the contents of the frame.
   */
  private void initialize() {
    frmDicomServer = new JFrame();
    frmDicomServer.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frmDicomServer.setBounds(100, 100, 700, 315);
    frmDicomServer.setTitle("DICOM Server");
    frmDicomServer.getContentPane().setLayout(new BorderLayout(0, 0));

    JPanel panel_1 = new JPanel();
    panel_1.setBorder(new TitledBorder(null, "DICOM Settings", TitledBorder.LEADING, TitledBorder.TOP, null, null));
    frmDicomServer.getContentPane().add(panel_1, BorderLayout.NORTH);
    panel_1.setLayout(new BorderLayout(0, 0));

    JPanel panel = new JPanel();
    panel_1.add(panel);
    panel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

    JLabel lblAeTitle = new JLabel("AE Title");
    panel.add(lblAeTitle);

    aeTitle = new JTextField();
    panel.add(aeTitle);
    aeTitle.setColumns(15);

    JLabel lblHostnameip = new JLabel("Hostname/IP");
    panel.add(lblHostnameip);

    hostname = new JComboBox(new DefaultComboBoxModel());
    panel.add(hostname);

    JLabel lblPort = new JLabel("Port");
    panel.add(lblPort);

    port = new JSpinner();
    panel.add(port);
    port.setModel(new SpinnerNumberModel(1100, 100, 65535, 1));

    JPanel panel_2 = new JPanel();
    FlowLayout flowLayout = (FlowLayout) panel_2.getLayout();
    panel_1.add(panel_2, BorderLayout.SOUTH);

    startStop = new JButton("Start");
    startStop.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        if(server.isRunning() == false) {
          getSettings().setAeTitle(aeTitle.getText());
          DefaultComboBoxModel c = ((DefaultComboBoxModel) hostname.getModel());
          getSettings().setHostname((String) c.getSelectedItem());
          getSettings().setPort((Integer) port.getValue());
          try {
            server.start();
          } catch(IOException e) {
            JOptionPane.showMessageDialog(frmDicomServer, e);
          }
        } else {
          server.stop();
        }
      }
    });
    panel_2.add(startStop);

    JPanel panel_3 = new JPanel();
    panel_3.setBorder(new TitledBorder(null, "Files Received", TitledBorder.LEADING, TitledBorder.TOP, null, null));
    frmDicomServer.getContentPane().add(panel_3, BorderLayout.CENTER);
    panel_3.setLayout(new BorderLayout(0, 0));

    table = new JTable();
    table.setModel(new DefaultTableModel(columns.toArray(), 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return column == columns.indexOf(LATERALITY) ? true : false;
      }
    });
    table.getColumnModel().getColumn(0).setResizable(false);
    table.getColumnModel().getColumn(0).setPreferredWidth(15);
    table.getColumnModel().getColumn(0).setMaxWidth(15);

    table.getColumnModel().getColumn(columns.indexOf(LATERALITY))
        .setCellEditor(new DefaultCellEditor(new JComboBox(new Object[] { "Left", "Right" })));
    panel_3.add(table, BorderLayout.CENTER);
    panel_3.add(table.getTableHeader(), BorderLayout.NORTH);

    JPanel panel_4 = new JPanel();
    frmDicomServer.getContentPane().add(panel_4, BorderLayout.SOUTH);

    JButton btnNewButton = new JButton("Save");
    btnNewButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Vector<Vector<Object>> data = getData();
        boolean missing = false;
        for(int i = 0; i < data.size(); i++) {
          String laterality = (String) data.get(i).get(columns.indexOf(LATERALITY));
          if(laterality == null || laterality.equals("")) {
            JOptionPane.showMessageDialog(null, "You need to choose laterality for each line");
            missing = true;
            break;
          }
        }
        if(missing == false) frmDicomServer.dispose();
      }
    });
    panel_4.add(btnNewButton);

    frmDicomServer.addWindowListener(new WindowAdapter() {

      @Override
      public void windowClosed(WindowEvent e) {
        try {
          server.stop();
        } finally {
          exitLatch.countDown();
        }
      }

    });
  }

  public Vector<Vector<Object>> getData() {
    return model == null ? new Vector<Vector<Object>>() : model.getDataVector();
  }

  private DicomSettings getSettings() {
    return server.getSettings();
  }

  private void bind() {
    aeTitle.setText(getSettings().getAeTitle());
    Vector<String> interfaces = getIPs();
    hostname.setModel(new DefaultComboBoxModel(interfaces));
    if(interfaces.contains(getSettings().getHostname())) {
      hostname.setSelectedItem(getSettings().getHostname());
    } else if(interfaces.size() > 0) {
      hostname.setSelectedIndex(0);
    }
    port.setValue(Integer.valueOf(getSettings().getPort()));
  }

  private Vector<String> getIPs() {
    Vector<String> ifaces = new Vector<String>();
    Enumeration<NetworkInterface> ni;
    try {
      ni = NetworkInterface.getNetworkInterfaces();
    } catch(SocketException e1) {
      return ifaces;
    }

    while(ni.hasMoreElements()) {
      NetworkInterface networkInterface = ni.nextElement();
      try {
        if(networkInterface.isUp()) {
          Enumeration<InetAddress> ias = networkInterface.getInetAddresses();
          while(ias.hasMoreElements()) {
            InetAddress inetAddress = (InetAddress) ias.nextElement();
            if(inetAddress instanceof Inet4Address) {
              ifaces.add(inetAddress.getHostAddress());
            }
          }
        }
      } catch(Exception e) {
        // ignore
      }
    }
    return ifaces;
  }

  public static void main(String[] args) throws IOException {
    File f = File.createTempFile("dcm", "");
    f.delete();
    f.mkdir();

    Set<String> output = new LinkedHashSet<>();
    Collections.addAll(output, "CINELOOP_1", "CINELOOP_2", "CINELOOP_3", "STILL_IMAGE", "SR");

    DicomSettings settings = new DicomSettings();
    settings.setPort(1100);
    settings.setAeTitle("DICOMSTORAGESCP");

    DicomStorageScp scp = new DicomStorageScp(new DicomServer(f, settings),
      new VividInstrumentRunner.VividDicomStoragePredicate(output, null));
    scp.show();
    scp.waitForExit();
  }

  private class DicomStorageListener implements StorageListener {

    List<String> storedFileNames = new ArrayList<>();

    @Override
    public void onStored(File file, DicomObject dicomObject) {
      if (storedFileNames.contains(file.getName())) return;

      storedFileNames.add(file.getName());

      String siuid = dicomObject.getString(Tag.StudyInstanceUID);

      if (dicomStoragePredicate == null) addRow(siuid, dicomObject);
      else if (dicomStoragePredicate.apply(siuid, file, dicomObject)) addRow(siuid, dicomObject);
      // else ignore that file
    }

    private void addRow(String siuid, DicomObject dicomObject) {
      model = (DefaultTableModel) table.getModel();
      int rows = model.getRowCount();


      int row = getRowBySIUID(siuid);
      log.info("Adding Dicom object with StudyInstanceUID {} at row {}", siuid, row);
      if(row == -1) {
        model.addRow(new Object[] { "" + (rows + 1), //
            dicomObject.getString(Tag.PatientID),//
            siuid, //
            1,//
            "" });
      } else {
        int columnIndex = columns.indexOf(NUMBER);
        int value = (Integer) getData().get(row).get(columnIndex);
        model.setValueAt(value + 1, row, columnIndex);
      }
    }

    private int getRowBySIUID(String siuid) {
      Vector<Vector<Object>> data = getData();
      for(int i = 0; i < data.size(); i++) {
        Vector<Object> row = data.get(i);
        String currentSiuid = (String) row.get(columns.indexOf(STUDYINSTANCEUID));
        if(currentSiuid.equals(siuid)) return i;
      }
      return -1;
    }
  }

  public interface DicomStoragePredicate {

    boolean apply(String siuid, File file, DicomObject dicomObject);

  }
}
