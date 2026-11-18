/*
 * Copyright 2012-2016, Wladimir Leite, Luis Filipe Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.engine.task.carver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.tika.mime.MediaType;

import iped.carvers.api.CarverConfiguration;
import iped.data.IItem;
import iped.engine.data.Item;
import iped.engine.task.AbstractTask;
import iped.engine.task.index.IndexItem;
import iped.engine.util.Util;
import iped.properties.MediaTypes;

/**
 * Classe base de tarefas de carving. Centraliza contador de itens carveados e
 * outros métodos comuns.
 */
public abstract class BaseCarveTask extends AbstractTask {

    public static MediaType UNALLOCATED_MIMETYPE = MediaTypes.UNALLOCATED;
    protected static MediaType mtPageFile = MediaType.application("x-pagefile"); //$NON-NLS-1$
    protected static MediaType mtVolumeShadow = MediaType.application("x-volume-shadow"); //$NON-NLS-1$

    public static final String FILE_FRAGMENT = "fileFragment"; //$NON-NLS-1$
    public static final String NUM_CARVED_AND_FRAGS = "numCarvedAndFrags";
    public static final String NUM_CARVED = "numCarved";
    public static final String CARVED_ID = "carvedId";

    protected static CarverConfiguration carverConfig = null;

    private static int itensCarved;

    private Set<Long> ledCarvedOffsets;
    private IItem prevEvidence;

    protected static final Map<IItem, Set<Long>> ledCarved = new HashMap<IItem, Set<Long>>();

    private final synchronized static void incItensCarved() {
        itensCarved++;
    }

    public final synchronized static int getItensCarved() {
        return itensCarved;
    }

    protected IItem addCarvedFile(IItem parentEvidence, long off, long len, String name, MediaType mediaType) {
        IItem carvedEvidence = createCarvedFile(parentEvidence, off, len, name, mediaType);
        if (carvedEvidence != null)
            addOffsetFile(carvedEvidence, parentEvidence);
        return carvedEvidence;
    }

    protected IItem createCarvedFile(IItem parentEvidence, long off, long len, String name, MediaType mediaType) {

        if (ledCarvedExists(parentEvidence, off))
            return null;

        Item carvedEvidence = getOffsetFile(parentEvidence, off, len, name, mediaType);
        configureOffsetItem(parentEvidence, carvedEvidence, off);
        carvedEvidence.setCarved(true);

        return carvedEvidence;
    }

    protected Item getOffsetFile(IItem parentEvidence, long off, long len, String name, MediaType mediaType) {
        Item offsetFile = new Item();
        offsetFile.setName(name);
        offsetFile.setPath(parentEvidence.getPath() + ">>" + name); //$NON-NLS-1$
        len = Math.min(len, parentEvidence.getLength() - off);
        offsetFile.setLength(len);
        offsetFile.setSumVolume(false);
        offsetFile.setParent(parentEvidence);

        offsetFile.setDeleted(parentEvidence.isDeleted());

        if (mediaType != null)
            offsetFile.setMediaType(mediaType);

        long prevOff = parentEvidence.getFileOffset();
        offsetFile.setFileOffset(prevOff == -1 ? off : prevOff + off);

        return offsetFile;
    }

    /**
     * Used to clear counters added to extra attributes of parent items, needed when
     * resuming processing, so those counters will start from zero.
     * 
     * @param parentFile
     */
    protected void clearExtraAttributes(IItem parentFile) {
        parentFile.getExtraAttributeMap().remove(NUM_CARVED);
        parentFile.getExtraAttributeMap().remove(NUM_CARVED_AND_FRAGS);
    }

    protected void addOffsetFile(IItem offsetFile, IItem parentEvidence) {

        if (offsetFile.isCarved()) {
            incItensCarved();
            Integer numCarved = (Integer) parentEvidence.getExtraAttribute(NUM_CARVED);
            if (numCarved == null) {
                numCarved = 0;
            }
            offsetFile.setExtraAttribute(CARVED_ID, numCarved);
            parentEvidence.setExtraAttribute(NUM_CARVED, numCarved + 1);
        }

        Number numSubitems = (Number) parentEvidence.getExtraAttribute(NUM_CARVED_AND_FRAGS);
        if (numSubitems == null) {
            numSubitems = 0;
        }
        parentEvidence.setExtraAttribute(NUM_CARVED_AND_FRAGS, numSubitems.intValue() + 1);

        worker.processNewItem(offsetFile);
    }

    protected boolean isToProcess(IItem evidence) {
        if (evidence.isCarved() || evidence.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) != null
                || !carverConfig.isToProcess(evidence.getMediaType())) {
            return false;
        }
        return true;
    }

    private boolean ledCarvedExists(IItem parentEvidence, long off) {
        if (!parentEvidence.equals(prevEvidence)) {
            synchronized (ledCarved) {
                ledCarvedOffsets = ledCarved.get(parentEvidence);
            }
            prevEvidence = parentEvidence;
        }
        if (ledCarvedOffsets != null && ledCarvedOffsets.contains(off)) {
            return true;
        } else
            return false;
    }

    protected void configureOffsetItem(IItem parentItem, Item carvedItem, long offset) {
        if (parentItem.getIdInDataSource() != null) {
            carvedItem.setIdInDataSource(parentItem.getIdInDataSource());
            carvedItem.setInputStreamFactory(parentItem.getInputStreamFactory());
        }

        // optimization to not create more temp files
        if (parentItem.hasTmpFile()) {
            try {
                carvedItem.setParentTmpFile(parentItem.getTempFile(), (Item) parentItem);
                carvedItem.setParentOffset(offset);
            } catch (IOException e) {
                // ignore
            }
        }
        parentItem.setHasChildren(true);

        carvedItem.setExtraAttribute(IndexItem.PARENT_TRACK_ID, Util.getTrackID(parentItem));
    }

    // adiciona uma evidência já carveada por uma classe que implemente a interface
    // Carver
    protected boolean addCarvedEvidence(Item parentEvidence, Item carvedEvidence, long off) {

        if (ledCarvedExists(parentEvidence, off))
            return false;

        carvedEvidence.setCarved(true);
        configureOffsetItem(parentEvidence, carvedEvidence, off);
        addOffsetFile(carvedEvidence, parentEvidence);

        return true;
    }
}