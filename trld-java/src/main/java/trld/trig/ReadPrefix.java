/**
 * This file was automatically generated by the TRLD transpiler.
 * Source: trld/trig/parser.py
 */
package trld.trig;

//import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.*;

import trld.Builtins;
import trld.KeyValue;

import trld.Input;
import static trld.Common.dumpJson;
import static trld.jsonld.Base.VALUE;
import static trld.jsonld.Base.TYPE;
import static trld.jsonld.Base.LANGUAGE;
import static trld.jsonld.Base.ID;
import static trld.jsonld.Base.LIST;
import static trld.jsonld.Base.GRAPH;
import static trld.jsonld.Base.CONTEXT;
import static trld.jsonld.Base.VOCAB;
import static trld.jsonld.Base.BASE;
import static trld.jsonld.Base.PREFIX;
import static trld.jsonld.Base.PREFIX_DELIMS;
import static trld.Rdfterms.RDF_TYPE;
import static trld.Rdfterms.XSD;
import static trld.Rdfterms.XSD_DOUBLE;
import static trld.Rdfterms.XSD_INTEGER;
import static trld.trig.Parser.*;


public class ReadPrefix extends ReadDecl { // LINE: 515
  ReadPrefix(ReadNodes parent, Boolean finalDot) { super(parent, finalDot); };
  public /*@Nullable*/ String pfx; // LINE: 517
  public /*@Nullable*/ String ns; // LINE: 518

  public void init() { // LINE: 520
    this.pfx = null; // LINE: 521
    this.ns = null; // LINE: 522
  }

  public boolean moreParts(Map value) { // LINE: 524
    if (this.pfx == null) { // LINE: 525
      String pfx = (String) ((String) value.get(SYMBOL)); // LINE: 526
      if (!pfx.equals("")) { // LINE: 527
        if (pfx.endsWith(":")) { // LINE: 528
          pfx = pfx.substring(0, pfx.length() - 1); // LINE: 529
        } else {
          throw new NotationError("Invalid prefix " + pfx); // LINE: 531
        }
      }
      this.pfx = pfx; // LINE: 532
      return true; // LINE: 533
    }
    if (this.ns == null) { // LINE: 535
      this.ns = (String) value.get(ID); // LINE: 536
    }
    return false; // LINE: 538
  }

  public void declare() { // LINE: 540
    Object ns = (Object) this.ns; // LINE: 541
    if ((!this.pfx.equals("") && !this.ns.equals("") && !(PREFIX_DELIMS.contains(this.ns.substring(this.ns.length() - 1, this.ns.length() - 1 + 1))))) { // LINE: 542
      ns = Builtins.mapOf(ID, this.ns, PREFIX, true); // LINE: 543
    }
    String key = ((this.pfx != null && !this.pfx.equals("")) ? this.pfx : VOCAB); // LINE: 544
    this.parent.context.put(key, ns); // LINE: 545
  }
}
