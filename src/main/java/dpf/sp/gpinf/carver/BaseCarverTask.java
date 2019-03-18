package dpf.sp.gpinf.carver;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import gpinf.dev.data.ItemImpl;
import iped3.Item;
import iped3.sleuthkit.SleuthKitItem;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Classe base de tarefas de carving. Centraliza contador de itens carveados e
 * outros métodos comuns.
 */
public abstract class BaseCarverTask extends AbstractTask  {
	private static Logger LOGGER = LoggerFactory.getLogger(AbstractTask.class);

	public static final String FILE_FRAGMENT = "fileFragment"; //$NON-NLS-1$
    protected static final Map<Item, Set<Long>> kffCarved = new HashMap<Item, Set<Long>>();
    protected static MediaType mtPageFile = MediaType.application("x-pagefile"); //$NON-NLS-1$
    protected static MediaType mtVolumeShadow = MediaType.application("x-volume-shadow"); //$NON-NLS-1$
    protected static MediaType mtDiskImage = MediaType.application("x-disk-image"); //$NON-NLS-1$
    protected static MediaType mtVmdk = MediaType.application("x-vmdk"); //$NON-NLS-1$
    protected static MediaType mtVhd = MediaType.application("x-vhd"); //$NON-NLS-1$
    protected static MediaType mtVdi = MediaType.application("x-vdi"); //$NON-NLS-1$
    protected static MediaType mtUnknown = MediaType.application("octet-stream"); //$NON-NLS-1$
    private static int itensCarved;
    private Set<Long> kffCarvedOffsets;
    private Item prevEvidence;

    private final synchronized static void incItensCarved() {
        itensCarved++;
    }

    public final synchronized static int getItensCarved() {
        return itensCarved;
    }

    protected void addOffsetFile(Item offsetFile, Item parentEvidence) {
        // Caso o item pai seja um subitem a ser excluído pelo filtro de exportação,
        // processa no worker atual
    	try {
            if (parentEvidence.isSubItem() && !parentEvidence.isToAddToCase()) {
                caseData.incDiscoveredEvidences(1);
                worker.process(offsetFile);
            } else {
                worker.processNewItem(offsetFile);
            }
    	}catch(Exception e) {
    	      LOGGER.warn("Unexpected carving error on {} - {}\t{}", parentEvidence.getPath(), offsetFile, this.getClass().getName()); //$NON-NLS-1$
    	}
    }

    protected boolean isToProcess(Item evidence) {
        if (evidence.isCarved() || evidence.getExtraAttribute(BaseCarverTask.FILE_FRAGMENT) != null) {
            return false;
        }
        return true;
    }

    // adiciona uma evidência já carveada por uma classe que implemente a interface
    // Carver
    protected boolean addCarvedEvidence(ItemImpl parentEvidence, ItemImpl carvedEvidence, long off) {
        if (!parentEvidence.equals(prevEvidence)) {
            synchronized (kffCarved) {
                kffCarvedOffsets = kffCarved.get(parentEvidence);
            }
            prevEvidence = parentEvidence;
        }
        if (kffCarvedOffsets != null && kffCarvedOffsets.contains(off)) {
            return false;
        }

        carvedEvidence.setCarved(true);
        incItensCarved();

        if ((parentEvidence instanceof SleuthKitItem) && ((SleuthKitItem)parentEvidence).getSleuthFile() != null) {
        	SleuthKitItem sparentEvidence = (SleuthKitItem) parentEvidence;
        	
            carvedEvidence.setSleuthFile(sparentEvidence.getSleuthFile());
            carvedEvidence.setSleuthId(sparentEvidence.getSleuthId());
            if (parentEvidence.hasTmpFile()) {
                try {
                    carvedEvidence.setFile(parentEvidence.getTempFile());
                    carvedEvidence.setTempStartOffset(off);
                } catch (IOException e) {
                    // ignore
                }
            }
        } else {
            carvedEvidence.setFile(parentEvidence.getFile());
            carvedEvidence.setExportedFile(parentEvidence.getExportedFile());
        }
        parentEvidence.setHasChildren(true);

        addOffsetFile(carvedEvidence, parentEvidence);

        return true;
    }

}