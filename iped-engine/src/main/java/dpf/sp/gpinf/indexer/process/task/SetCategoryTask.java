/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de EvidÃªncias Digitais (IPED).
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
package dpf.sp.gpinf.indexer.process.task;

import java.util.Arrays;
import java.util.List;

import dpf.sp.gpinf.indexer.config.CategoryConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import iped3.IItem;
import iped3.util.BasicProps;
import macee.core.Configurable;

/**
 * Classe que carrega o mapeamento de mimeTypes para Categoria da aplicação.
 * Além disso utiliza regras javascript de definição de categorias baseadas nas
 * propriedades dos itens. Também é responsável por definir a categoria do item.
 */
public class SetCategoryTask extends AbstractTask {

    private static String FOLDER_CATEGORY = "Folders"; //$NON-NLS-1$
    public static String SCANNED_CATEGORY = "Scanned Documents"; //$NON-NLS-1$

    private CategoryConfig categoryConfig;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new CategoryConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        categoryConfig = configurationManager.findObject(CategoryConfig.class);
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    public void process(IItem e) throws Exception {

        if (e.isDir()) {
            e.setCategory(FOLDER_CATEGORY);
        } else {
            String category = categoryConfig.getCategory(e.getMediaType());
            e.setCategory(category);
        }

        String category;
        if (e.isDir()) {
            category = FOLDER_CATEGORY;
        } else {
            category = categoryConfig.getCategory(e.getMediaType());
        }
        e.setCategory(category);
        e.setTempAttribute(BasicProps.CATEGORY, category);

    }

}
