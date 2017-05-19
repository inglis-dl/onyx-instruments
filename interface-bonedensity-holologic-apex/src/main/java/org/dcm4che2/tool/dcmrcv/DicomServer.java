package org.dcm4che2.tool.dcmrcv;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.PDVInputStream;
import org.obiba.onyx.jade.instrument.holologic.DicomSettings;

public class DicomServer {

  public enum State {
    STARTED, STOPPED
  }

  public interface StorageListener {
    public void onStored(File file, DicomObject dicomObject);
  }

  public interface StateListener {
    public void onStateChange(State newState);
  }

  public class StoredDicomFile {

    private final File file;

    public StoredDicomFile(File file) {
      this.file = file;
    }

    public File getFile() {
      return file;
    }

    public DicomObject getDicomObject() throws IOException {
      DicomInputStream dis = new DicomInputStream(new FileInputStream(file));
      try {
        return dis.readDicomObject();
      } finally {
        try {
          dis.close();
        } catch(Exception e) {
          // ignore
        }
      }
    }
  }

  private final File storage;

  private final DicomSettings settings;

  private final List<StorageListener> listeners = new ArrayList<StorageListener>();

  private final List<StateListener> stateListeners = new ArrayList<StateListener>();

  private final Set<File> dirtyFileCache = new HashSet<File>();

  private State state;

  private DcmRcv dcmRcv;

  public DicomServer(File storage, DicomSettings settings) {
    this.storage = storage;
    this.settings = settings;
  }

  public void addStorageListener(StorageListener listener) {
    if(listener != null) listeners.add(listener);
  }

  public void addStateListener(StateListener listener) {
    if(listener != null) stateListeners.add(listener);
  }

  public void start() throws IOException {
    DcmRcv dcm = new DcmRcv() {
      @Override
      void onCStoreRQ(Association as, int pcid, DicomObject rq, PDVInputStream dataStream, String tsuid, DicomObject rsp) throws IOException {
        super.onCStoreRQ(as, pcid, rq, dataStream, tsuid, rsp);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        File file = new File(storage, iuid);
        if(file.exists()) {
          try {
            DicomInputStream dis = new DicomInputStream(file);
            DicomObject dcm = dis.readDicomObject();
            onDicomFile(file, dcm);
          } catch(IOException e) {
            // ignore
          }
        }
      }

    };
    dcm.setAEtitle(settings.getAeTitle());
    dcm.setHostname(settings.getHostname());
    dcm.setPort(settings.getPort());
    dcm.setDestination(storage.getAbsolutePath());
    dcm.setStgCmtPort(settings.getStgCmtPort());
    dcm.initTransferCapability();
    dcm.start();

    this.dcmRcv = dcm;
    changeState(State.STARTED);
  }

  public void stop() {
    if(dcmRcv != null) {
      try {
        dcmRcv.stop();
      } finally {
        changeState(State.STOPPED);
        dcmRcv = null;
      }
    }
  }

  public boolean isRunning() {
    return state == State.STARTED;
  }

  public State getState() {
    return state;
  }

  public DicomSettings getSettings() {
    return settings;
  }

  /**
   * Return dicom files. (Oldest to Latest date modified)
   *
   * @return
   */
  public List<StoredDicomFile> listSortedDicomFiles() {
    File files[] = storage.listFiles();
    if(files != null) {
      List<StoredDicomFile> storedFiles = new ArrayList<StoredDicomFile>(files.length);
      for(File file : files) {
        if(!dirtyFileCache.contains(file)) {
          storedFiles.add(new StoredDicomFile(file));
        }
      }
      Collections.sort(storedFiles, new Comparator<StoredDicomFile>() {

        @Override
        public int compare(StoredDicomFile o1, StoredDicomFile o2) {
          return new Long(o1.getFile().lastModified()).compareTo(o2.getFile().lastModified());
        }
      });

      return storedFiles;
    }
    return Collections.emptyList();
  }

  /**
   * Cache dicom files to be excluded from the list
   * returned by listSortedDicomFiles.
   *
   * @param file
   */
  public void cacheDirtyFile(StoredDicomFile file) {
    dirtyFileCache.add(file.getFile());
  }

  private void changeState(State newState) {
    this.state = newState;
    for(StateListener l : this.stateListeners) {
      l.onStateChange(newState);
    }
  }

  private void onDicomFile(File file, DicomObject dcm) {
    for(StorageListener l : this.listeners) {
      l.onStored(file, dcm);
    }
  }

}
