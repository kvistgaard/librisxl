package se.kb.libris.whelks.plugin;

import se.kb.libris.whelks.Document;
import se.kb.libris.whelks.Whelk;

public interface IndexFormatConverter extends Plugin {
    public Document convert(Document doc);
}
