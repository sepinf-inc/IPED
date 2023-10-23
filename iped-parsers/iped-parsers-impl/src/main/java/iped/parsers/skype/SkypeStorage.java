package iped.parsers.skype;

import java.io.Closeable;
import java.sql.Connection;
import java.util.Collection;

import iped.search.IItemSearcher;

public interface SkypeStorage extends Closeable {

    void searchMediaCache(IItemSearcher searcher);

    Collection<SkypeConversation> extraiMensagens() throws SkypeParserException;

    Collection<SkypeContact> extraiContatos() throws SkypeParserException;

    Collection<SkypeFileTransfer> extraiTransferencias() throws SkypeParserException;

    String getSkypeName();

    SkypeAccount getAccount();

    Connection getConnection() throws SkypeParserException;

}