package dpf.mt.gpinf.skype.parser;

import java.io.Closeable;
import java.util.Collection;
import iped3.search.IItemSearcher;

public interface SkypeStorage extends Closeable {

	void searchMediaCache(IItemSearcher searcher);

	Collection<SkypeConversation> extraiMensagens() throws SkypeParserException;

	Collection<SkypeContact> extraiContatos() throws SkypeParserException;

	Collection<SkypeFileTransfer> extraiTransferencias() throws SkypeParserException;

	String getSkypeName();

	SkypeAccount getAccount();

}