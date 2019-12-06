package dpf.mt.gpinf.skype.parser;

import java.util.Date;

/**
 * Classe que representa uma mensagem registrada no arquivo main.db.
 *
 * @author Patrick Dalla Bernardina patrick.pdb@dpf.gov.br
 */

public class SkypeMessage {

    String id;
    String conteudo;
    String autor;
    String destino;
    Date data;
    SkypeConversation conversation;
    int tipo;
    int chatMessageStatus;
    int SendingStatus;
    boolean fromMe;
    Date dataEdicao;
    String editor;
    int idRemoto;
    SkypeMessageUrlFile anexoUri = null;

    /* Message Types */
    public final int POSTED_TEXT = 61;
    public final int POSTED_EMOTE = 60;
    public final int POSTED_CONTACTS = 63;
    public final int POSTED_VOICE_MESSAGE = 67;
    public final int POSTED_FILE = 68;
    public final int HAS_BIRTHDAY = 110;
    public final int ADDED_CONSUMERS = 10;
    public final int BLOCKED = 53;
    public final int INVITE_SENT = 50;
    public final int INVITE_ACCEPT = 51;
    public final int CALL_START = 30;
    public final int CALL_END = 39;

    /* Message Sending Status types */
    public static final short SE_MSG_NAO_ENVIADA = 1; // representa uma mensagem digitada e com o botão enviar
                                                      // pressionado mas não enviada de fato ainda por qualquer motivo.

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConteudo() {
        return conteudo;
    }

    public void setConteudo(String conteudo) {
        this.conteudo = conteudo;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public String getTitle() {
        if (conteudo != null) {
            int endIndex = conteudo.length();
            endIndex = endIndex < 10 ? endIndex : 10;
            return id + "-" + autor + "-" + conteudo.substring(0, endIndex) + "..."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } else {
            return id + "-" + autor + "-"; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    public Date getData() {
        return data;
    }

    public void setData(Date data) {
        this.data = data;
    }

    public SkypeConversation getConversation() {
        return conversation;
    }

    public void setConversation(SkypeConversation conversation) {
        this.conversation = conversation;
    }

    public boolean isRecebidaNoDestino() {
        return (chatMessageStatus == 2 || chatMessageStatus == 4) && isEnviada();
    }

    public boolean isEnviada() {
        return (SendingStatus != SE_MSG_NAO_ENVIADA) && (SendingStatus != 3);
    }

    public boolean isRecebidaLocalmente() {
        return chatMessageStatus == 4;
    }

    public int getTipo() {
        return tipo;
    }

    public void setTipo(int tipo) {
        this.tipo = tipo;
    }

    public int getChatMessageStatus() {
        return chatMessageStatus;
    }

    public void setChatMessageStatus(int chatMessageStatus) {
        this.chatMessageStatus = chatMessageStatus;
    }

    public int getSendingStatus() {
        return SendingStatus;
    }

    public void setSendingStatus(int sendingStatus) {
        SendingStatus = sendingStatus;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public void setFromMe(boolean fromMe) {
        this.fromMe = fromMe;
    }

    public Date getDataEdicao() {
        return dataEdicao;
    }

    public void setDataEdicao(Date dataEdicao) {
        this.dataEdicao = dataEdicao;
    }

    public String getEditor() {
        return editor;
    }

    public void setEditor(String editor) {
        this.editor = editor;
    }

    public int getIdRemoto() {
        return idRemoto;
    }

    public void setIdRemoto(int idRemoto) {
        this.idRemoto = idRemoto;
    }

    public SkypeMessageUrlFile getAnexoUri() {
        return anexoUri;
    }

    public void setAnexoUri(SkypeMessageUrlFile anexoUri) {
        this.anexoUri = anexoUri;
    }
}
