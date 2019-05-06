package gpinf.video;

import java.io.File;

/*
 * Copyright 2015-2015, Wladimir Leite
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
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
/**
 * Classe de armazenamento de dados de configuração de formatos de saída do processo de geração de
 * imagens com cenas extraídas de vídeos.
 *
 * @author Wladimir Leite
 */
public class VideoThumbsOutputConfig {

  private File outFile;
  private int thumbWidth, rows, columns, border;

  public VideoThumbsOutputConfig(File outFile, int thumbWidth, int columns, int rows, int border) {
    this.outFile = outFile;
    this.thumbWidth = thumbWidth;
    this.rows = rows;
    this.columns = columns;
    this.border = border;
  }

  public int getBorder() {
    return border;
  }

  public int getThumbWidth() {
    return thumbWidth;
  }

  public File getOutFile() {
    return outFile;
  }

  public int getRows() {
    return rows;
  }

  public int getColumns() {
    return columns;
  }

  public void setOutFile(File outFile) {
    this.outFile = outFile;
  }

  public void setThumbWidth(int thumbWidth) {
    this.thumbWidth = thumbWidth;
  }

  public void setRows(int rows) {
    this.rows = rows;
  }

  public void setColumns(int columns) {
    this.columns = columns;
  }

  public void setBorder(int border) {
    this.border = border;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("VideoThumbsOutputConfig [imageFile="); //$NON-NLS-1$
    builder.append(outFile);
    builder.append(", thumbWidth="); //$NON-NLS-1$
    builder.append(thumbWidth);
    builder.append(", rows="); //$NON-NLS-1$
    builder.append(rows);
    builder.append(", columns="); //$NON-NLS-1$
    builder.append(columns);
    builder.append(", border="); //$NON-NLS-1$
    builder.append(border);
    builder.append("]"); //$NON-NLS-1$
    return builder.toString();
  }
}
