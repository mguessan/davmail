/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLReporter2;
import org.codehaus.stax2.XMLStreamLocation2;
import org.codehaus.stax2.validation.XMLValidationProblem;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.InputConfigFlags;
import com.ctc.wstx.cfg.ParsingErrorMsgs;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.dtd.MinimalDTDReader;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.ent.IntEntity;
import com.ctc.wstx.exc.*;
import com.ctc.wstx.io.DefaultInputResolver;
import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.io.WstxInputLocation;
import com.ctc.wstx.io.WstxInputSource;
import com.ctc.wstx.util.ExceptionUtil;
import com.ctc.wstx.util.SymbolTable;
import com.ctc.wstx.util.TextBuffer;

/**
 * Abstract base class that defines some basic functionality that all
 * Woodstox reader classes (main XML reader, DTD reader) extend from.
 */

public abstract class StreamScanner
    extends WstxInputData
    implements InputProblemReporter,
        InputConfigFlags, ParsingErrorMsgs
{

    // // // Some well-known chars:

    /**
     * Last (highest) char code of the three, LF, CR and NULL
     */
    public final static char CHAR_CR_LF_OR_NULL = (char) 13;

    public final static int INT_CR_LF_OR_NULL = 13;

    /**
     * Character that allows quick check of whether a char can potentially
     * be some kind of markup, WRT input stream processing;
     * has to contain linefeeds, &, < and > (">" only matters when
     * quoting text, as part of "]]>")
     */
    protected final static char CHAR_FIRST_PURE_TEXT = (char) ('>' + 1);


    /**
     * First character in Unicode (ie one with lowest id) that is legal
     * as part of a local name (all valid name chars minus ':'). Used
     * for doing quick check for local name end; usually name ends in
     * a whitespace or equals sign.
     */
    protected final static char CHAR_LOWEST_LEGAL_LOCALNAME_CHAR = '-';

    /*
    ///////////////////////////////////////////////////////////
    // Character validity constants, structs
    ///////////////////////////////////////////////////////////
     */

    /**
     * We will only use validity array for first 256 characters, mostly
     * because after those characters it's easier to do fairly simple
     * block checks.
     */
    private final static int VALID_CHAR_COUNT = 0x100;

    private final static byte NAME_CHAR_INVALID_B = (byte) 0;
    private final static byte NAME_CHAR_ALL_VALID_B = (byte) 1;
    private final static byte NAME_CHAR_VALID_NONFIRST_B = (byte) -1;

    private final static byte[] sCharValidity = new byte[VALID_CHAR_COUNT];

    static {
        /* First, since all valid-as-first chars are also valid-as-other chars,
         * we'll initialize common chars:
         */
        sCharValidity['_'] = NAME_CHAR_ALL_VALID_B;
        for (int i = 0, last = ('z' - 'a'); i <= last; ++i) {
            sCharValidity['A' + i] = NAME_CHAR_ALL_VALID_B;
            sCharValidity['a' + i] = NAME_CHAR_ALL_VALID_B;
        }
        for (int i = 0xC0; i < 0xF6; ++i) { // not all are fully valid, but
            sCharValidity[i] = NAME_CHAR_ALL_VALID_B;
        }
        // ... now we can 'revert' ones not fully valid:
        sCharValidity[0xD7] = NAME_CHAR_INVALID_B;
        sCharValidity[0xF7] = NAME_CHAR_INVALID_B;

        /* And then we can proceed with ones only valid-as-other.
         */
        sCharValidity['-'] = NAME_CHAR_VALID_NONFIRST_B;
        sCharValidity['.'] = NAME_CHAR_VALID_NONFIRST_B;
        sCharValidity[0xB7] = NAME_CHAR_VALID_NONFIRST_B;
        for (int i = '0'; i <= '9'; ++i) {
            sCharValidity[i] = NAME_CHAR_VALID_NONFIRST_B;
        }
    }

    /**
     * Public identifiers only use 7-bit ascii range.
     */
    private final static int VALID_PUBID_CHAR_COUNT = 0x80;
    private final static byte[] sPubidValidity = new byte[VALID_PUBID_CHAR_COUNT];
//    private final static byte PUBID_CHAR_INVALID_B = (byte) 0;
    private final static byte PUBID_CHAR_VALID_B = (byte) 1;
    static {
        for (int i = 0, last = ('z' - 'a'); i <= last; ++i) {
            sPubidValidity['A' + i] = PUBID_CHAR_VALID_B;
            sPubidValidity['a' + i] = PUBID_CHAR_VALID_B;
        }
        for (int i = '0'; i <= '9'; ++i) {
            sPubidValidity[i] = PUBID_CHAR_VALID_B;
        }

        // 3 main white space types are valid
        sPubidValidity[0x0A] = PUBID_CHAR_VALID_B;
        sPubidValidity[0x0D] = PUBID_CHAR_VALID_B;
        sPubidValidity[0x20] = PUBID_CHAR_VALID_B;

        // And many of punctuation/separator ascii chars too:
        sPubidValidity['-'] = PUBID_CHAR_VALID_B;
        sPubidValidity['\''] = PUBID_CHAR_VALID_B;
        sPubidValidity['('] = PUBID_CHAR_VALID_B;
        sPubidValidity[')'] = PUBID_CHAR_VALID_B;
        sPubidValidity['+'] = PUBID_CHAR_VALID_B;
        sPubidValidity[','] = PUBID_CHAR_VALID_B;
        sPubidValidity['.'] = PUBID_CHAR_VALID_B;
        sPubidValidity['/'] = PUBID_CHAR_VALID_B;
        sPubidValidity[':'] = PUBID_CHAR_VALID_B;
        sPubidValidity['='] = PUBID_CHAR_VALID_B;
        sPubidValidity['?'] = PUBID_CHAR_VALID_B;
        sPubidValidity[';'] = PUBID_CHAR_VALID_B;
        sPubidValidity['!'] = PUBID_CHAR_VALID_B;
        sPubidValidity['*'] = PUBID_CHAR_VALID_B;
        sPubidValidity['#'] = PUBID_CHAR_VALID_B;
        sPubidValidity['@'] = PUBID_CHAR_VALID_B;
        sPubidValidity['$'] = PUBID_CHAR_VALID_B;
        sPubidValidity['_'] = PUBID_CHAR_VALID_B;
        sPubidValidity['%'] = PUBID_CHAR_VALID_B;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Basic configuration
    ///////////////////////////////////////////////////////////
     */

    /**
     * Copy of the configuration object passed by the factory.
     * Contains immutable settings for this reader (or in case
     * of DTD parsers, reader that uses it)
     */
    protected final ReaderConfig mConfig;

    // // // Various extracted settings:

    /**
     * If true, Reader is namespace aware, and should do basic checks
     * (usually enforcing limitations on having colons in names)
     */
    protected final boolean mCfgNsEnabled;

    // Extracted standard on/off settings:

    /**
     * note: left non-final on purpose: sub-class may need to modify
     * the default value after construction.
     */
    protected boolean mCfgReplaceEntities;

    /*
    ///////////////////////////////////////////////////////////
    // Symbol handling, if applicable
    ///////////////////////////////////////////////////////////
     */

    final SymbolTable mSymbols;

    /**
     * Local full name for the event, if it has one (note: element events
     * do NOT use this variable; those names are stored in element stack):
     * target for processing instructions.
     *<p>
     * Currently used for proc. instr. target, and entity name (at least
     * when current entity reference is null).
     *<p>
     * Note: this variable is generally not cleared, since it comes from
     * a symbol table, ie. this won't be the only reference.
     */
    protected String mCurrName;

    /*
    ///////////////////////////////////////////////////////////
    // Input handling
    ///////////////////////////////////////////////////////////
     */

    /**
     * Currently active input source; contains link to parent (nesting) input
     * sources, if any.
     */
    protected WstxInputSource mInput;

    /**
     * Top-most input source this reader can use; due to input source
     * chaining, this is not necessarily the root of all input; for example,
     * external DTD subset reader's root input still has original document
     * input as its parent.
     */
    protected final WstxInputSource mRootInput;

    /**
     * Custom resolver used to handle external entities that are to be expanded
     * by this reader (external param/general entity expander)
     */
    XMLResolver mEntityResolver = null;

    /**
     * This is the current depth of the input stack (same as what input
     * element stack would return as its depth).
     * It is used to enforce input scope constraints for nesting of
     * elements (for xml reader) and dtd declaration (for dtd reader)
     * with regards to input block (entity expansion) boundaries.
     *<p>
     * Basically this value is compared to {@link #mInputTopDepth}, which
     * indicates what was the depth at the point where the currently active
     * input scope/block was started.
     */
    protected int mCurrDepth = 0;

    protected int mInputTopDepth = 0;

    /**
     * Flag that indicates whether linefeeds in the input data are to
     * be normalized or not.
     * Xml specs mandate that the line feeds are only normalized
     * when they are from the external entities (main doc, external
     * general/parsed entities), so normalization has to be
     * suppressed when expanding internal general/parsed entities.
     */
    protected boolean mNormalizeLFs;

    /**
     * Flag that indicates whether all escaped chars are accepted in XML 1.0.
     */
    protected boolean mXml10AllowAllEscapedChars = true;


    /*
    ///////////////////////////////////////////////////////////
    // Buffer(s) for local name(s) and text content
    ///////////////////////////////////////////////////////////
     */

    /**
     * Temporary buffer used if local name can not be just directly
     * constructed from input buffer (name is on a boundary or such).
     */
    protected char[] mNameBuffer = null;

    /*
    ///////////////////////////////////////////////////////////
    // Information about starting location of event
    // Reader is pointing to; updated on-demand
    ///////////////////////////////////////////////////////////
     */

    // // // Location info at point when current token was started

    /**
     * Total number of characters read before start of current token.
     * For big (gigabyte-sized) sizes are possible, needs to be long,
     * unlike pointers and sizes related to in-memory buffers.
     */
    protected long mTokenInputTotal = 0; 

    /**
     * Input row on which current token starts, 1-based
     */
    protected int mTokenInputRow = 1;

    /**
     * Column on input row that current token starts; 0-based (although
     * in the end it'll be converted to 1-based)
     */
    protected int mTokenInputCol = 0;

    /*
    ///////////////////////////////////////////////////////////
    // XML document information (from doc decl if one
    // was found) common to all entities (main xml
    // document, external DTD subset)
    ///////////////////////////////////////////////////////////
     */

    /**
     * Input stream encoding, if known (passed in, or determined by
     * auto-detection); null if not.
     */
    String mDocInputEncoding = null;

    /**
     * Character encoding from xml declaration, if any; null if no
     * declaration, or it didn't specify encoding.
     */
    String mDocXmlEncoding = null;

    /**
     * XML version as declared by the document; one of constants
     * from {@link XmlConsts} (like {@link XmlConsts#XML_V_10}).
     */
    protected int mDocXmlVersion = XmlConsts.XML_V_UNKNOWN;
    
    /**
     * Cache of internal character entities;
     */
    protected Map mCachedEntities;
    
    /**
     * Flag for whether or not character references should be treated as entities
     */
    protected boolean mCfgTreatCharRefsAsEntities;
    
    /**
     * Entity reference stream currently points to.
     */
    protected EntityDecl mCurrEntity;

    /*
    ///////////////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////////////
     */

    /**
     * Constructor used when creating a complete new (main-level) reader that
     * does not share its input buffers or state with another reader.
     */
    protected StreamScanner(WstxInputSource input, ReaderConfig cfg,
                            XMLResolver res)
    {
        super();
        mInput = input;
        // 17-Jun-2004, TSa: Need to know root-level input source
        mRootInput = input;

        mConfig = cfg;
        mSymbols = cfg.getSymbols();
        int cf = cfg.getConfigFlags();
        mCfgNsEnabled = (cf & CFG_NAMESPACE_AWARE) != 0;
        mCfgReplaceEntities = (cf & CFG_REPLACE_ENTITY_REFS) != 0;

        mNormalizeLFs = mConfig.willNormalizeLFs();
        mInputBuffer = null;
        mInputPtr = mInputEnd = 0;
        mEntityResolver = res;
        
        mCfgTreatCharRefsAsEntities = mConfig.willTreatCharRefsAsEnts();
        mCachedEntities = mCfgTreatCharRefsAsEntities ? new HashMap() : Collections.EMPTY_MAP;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Package API
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method that returns location of the last character returned by this
     * reader; that is, location "one less" than the currently pointed to
     * location.
     */
    protected WstxInputLocation getLastCharLocation()
    {
        return mInput.getLocation(mCurrInputProcessed + mInputPtr - 1,
                                  mCurrInputRow,
                                  mInputPtr - mCurrInputRowStart);
    }

    protected URL getSource() {
        return mInput.getSource();
    }

    protected String getSystemId() {
        return mInput.getSystemId();
    }

    /*
    ///////////////////////////////////////////////////////////
    // Partial LocationInfo implementation (not implemented
    // by this base class, but is by some sub-classes)
    ///////////////////////////////////////////////////////////
     */

    /**
     * Returns location of last properly parsed token; as per StAX specs,
     * apparently needs to be the end of current event, which is the same
     * as the start of the following event (or EOF if that's next).
     */
    public abstract Location getLocation();

    public XMLStreamLocation2 getStartLocation()
    {
        // note: +1 is used as columns are 1-based...
        return mInput.getLocation(mTokenInputTotal, mTokenInputRow,
                                  mTokenInputCol + 1);
    }

    public XMLStreamLocation2 getCurrentLocation()
    {
        return mInput.getLocation(mCurrInputProcessed + mInputPtr,
                                  mCurrInputRow,
                                  mInputPtr - mCurrInputRowStart + 1);
    }

    /*
    ///////////////////////////////////////////////////////////
    // InputProblemReporter implementation
    ///////////////////////////////////////////////////////////
     */

    public WstxException throwWfcException(String msg, boolean deferErrors)
        throws WstxException
    {
        WstxException ex = constructWfcException(msg);
        if (!deferErrors) {
            throw ex;
        }
        return ex;
    }

    public void throwParseError(String msg) throws XMLStreamException
    {
        throwParseError(msg, null, null);
    }

    /**
     * Throws generic parse error with specified message and current parsing
     * location.
     *<p>
     * Note: public access only because core code in other packages needs
     * to access it.
     */
    public void throwParseError(String format, Object arg, Object arg2)
        throws XMLStreamException
    {
        String msg = (arg != null || arg2 != null) ?
            MessageFormat.format(format, new Object[] { arg, arg2 }) : format;
        throw constructWfcException(msg);
    }

    public void reportProblem(String probType, String format, Object arg, Object arg2)
        throws XMLStreamException
    {
        XMLReporter rep = mConfig.getXMLReporter();
        if (rep != null) {
            _reportProblem(rep, probType,
                            MessageFormat.format(format, new Object[] { arg, arg2 }), null);
        }
    }

    public void reportProblem(Location loc, String probType,
                              String format, Object arg, Object arg2)
        throws XMLStreamException
    {
        XMLReporter rep = mConfig.getXMLReporter();
        if (rep != null) {
            String msg = (arg != null || arg2 != null) ?
                MessageFormat.format(format, new Object[] { arg, arg2 }) : format;
            _reportProblem(rep, probType, msg, loc);
        }
    }

    protected void _reportProblem(XMLReporter rep, String probType, String msg, Location loc)
        throws XMLStreamException
    {
        if (loc == null) {
            loc = getLastCharLocation();
        }
        _reportProblem(rep, new XMLValidationProblem(loc, msg, XMLValidationProblem.SEVERITY_ERROR, probType));
    }

    protected void _reportProblem(XMLReporter rep, XMLValidationProblem prob)
        throws XMLStreamException
    {
        if (rep != null) {
            Location loc = prob.getLocation();
            if (loc == null) {
                loc = getLastCharLocation();
                prob.setLocation(loc);
            }
            // Backwards-compatibility fix: add non-null type, if missing:
            if (prob.getType() == null) {
                prob.setType(ErrorConsts.WT_VALIDATION);
            }
            // [WSTX-154]: was catching and dropping thrown exception: shouldn't.
            // [WTSX-157]: need to support XMLReporter2
            if (rep instanceof XMLReporter2) {
                ((XMLReporter2) rep).report(prob);
            } else {
                rep.report(prob.getMessage(), prob.getType(), prob, loc);
            }
        }
    }

    /**
     *<p>
     * Note: this is the base implementation used for implementing
     * <code>ValidationContext</code>
     */
    public void reportValidationProblem(XMLValidationProblem prob)
        throws XMLStreamException
    {
        // !!! TBI: Fail-fast vs. deferred modes?
        /* For now let's implement basic functionality: warnings get
         * reported via XMLReporter, errors and fatal errors result in
         * immediate exceptions.
         */
        /* 27-May-2008, TSa: [WSTX-153] Above is incorrect: as per Stax
         *   javadocs for XMLReporter, both warnings and non-fatal errors
         *   (which includes all validation errors) should be reported via
         *   XMLReporter interface, and only fatals should cause an
         *   immediate stream exception (by-passing reporter)
         */
        if (prob.getSeverity() > XMLValidationProblem.SEVERITY_ERROR) {
            throw WstxValidationException.create(prob);
        }
        XMLReporter rep = mConfig.getXMLReporter();
        if (rep != null) {
            _reportProblem(rep, prob);
        } else {
            /* If no reporter, regular non-fatal errors are to be reported
             * as exceptions as well, for backwards compatibility
             */
            if (prob.getSeverity() >= XMLValidationProblem.SEVERITY_ERROR) {
                throw WstxValidationException.create(prob);
            }
        }
    }

    public void reportValidationProblem(String msg, int severity)
        throws XMLStreamException
    {
        reportValidationProblem(new XMLValidationProblem(getLastCharLocation(),
                                                         msg, severity));
    }

    public void reportValidationProblem(String msg)
        throws XMLStreamException
    {
        reportValidationProblem(new XMLValidationProblem(getLastCharLocation(),
                                                         msg,
                                                         XMLValidationProblem.SEVERITY_ERROR));
    }

    public void reportValidationProblem(Location loc, String msg)
        throws XMLStreamException
    {
        reportValidationProblem(new XMLValidationProblem(loc, msg));
    }

    public void reportValidationProblem(String format, Object arg, Object arg2)
        throws XMLStreamException
    {
        reportValidationProblem(MessageFormat.format(format, new Object[] { arg, arg2 }));
    }

    /*
    ///////////////////////////////////////////////////////////
    // Other error reporting methods
    ///////////////////////////////////////////////////////////
     */

    protected WstxException constructWfcException(String msg)
    {
        return new WstxParsingException(msg, getLastCharLocation());
    }

    /**
     * Construct and return a {@link XMLStreamException} to throw
     * as a result of a failed Typed Access operation (but one not
     * caused by a Well-Formedness Constraint or Validation Constraint
     * problem)
     */
    /*
    protected WstxException _constructTypeException(String msg)
    {
        // Hmmh. Should there be a distinct sub-type?
        return new WstxParsingException(msg, getLastCharLocation());
    }
    */

    protected WstxException constructFromIOE(IOException ioe)
    {
        return new WstxIOException(ioe);
    }

    protected WstxException constructNullCharException()
    {
        return new WstxUnexpectedCharException("Illegal character (NULL, unicode 0) encountered: not valid in any content",
                getLastCharLocation(), CHAR_NULL);
    }

    protected void throwUnexpectedChar(int i, String msg)
        throws WstxException
    {
        char c = (char) i;
        String excMsg = "Unexpected character "+getCharDesc(c)+msg;
        throw new WstxUnexpectedCharException(excMsg, getLastCharLocation(), c);
    }

    protected void throwNullChar()
        throws WstxException
    {
        throw constructNullCharException();
    }

    protected void throwInvalidSpace(int i)
        throws WstxException
    {
        throwInvalidSpace(i, false);
    }

    protected WstxException throwInvalidSpace(int i, boolean deferErrors)
        throws WstxException
    {
        char c = (char) i;
        WstxException ex;
        if (c == CHAR_NULL) {
            ex = constructNullCharException();
        } else {
            String msg = "Illegal character ("+getCharDesc(c)+")";
            if (mXml11) {
                msg += " [note: in XML 1.1, it could be included via entity expansion]";
            }
            ex = new WstxUnexpectedCharException(msg, getLastCharLocation(), c);
        }
        if (!deferErrors) {
            throw ex;
        }
        return ex;
    }

    protected void throwUnexpectedEOF(String msg)
        throws WstxException
    {
        throw new WstxEOFException("Unexpected EOF"
                                   +(msg == null ? "" : msg),
                                   getLastCharLocation());
    }

    /**
     * Similar to {@link #throwUnexpectedEOF}, but only indicates ending
     * of an input block. Used when reading a token that can not span
     * input block boundaries (ie. can not continue past end of an
     * entity expansion).
     */
    protected void throwUnexpectedEOB(String msg)
        throws WstxException
    {
        throw new WstxEOFException("Unexpected end of input block"
                                   +(msg == null ? "" : msg),
                                   getLastCharLocation());
    }

    protected void throwFromIOE(IOException ioe)
        throws WstxException
    {
        throw new WstxIOException(ioe);
    }

    protected void throwFromStrE(XMLStreamException strex)
        throws WstxException
    {
        if (strex instanceof WstxException) {
            throw (WstxException) strex;
        }
        WstxException newEx = new WstxException(strex);
        ExceptionUtil.setInitCause(newEx, strex);
        throw newEx;
    }

    /**
     * Method called to report an error, when caller's signature only
     * allows runtime exceptions to be thrown.
     */
    protected void throwLazyError(Exception e)
    {
        if (e instanceof XMLStreamException) {
            WstxLazyException.throwLazily((XMLStreamException) e);
        }
        ExceptionUtil.throwRuntimeException(e);
    }

    protected String tokenTypeDesc(int type)
    {
        return ErrorConsts.tokenTypeDesc(type);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Input buffer handling
    ///////////////////////////////////////////////////////////
     */

    /**
     * Returns current input source this source uses.
     *<p>
     * Note: public only because some implementations are on different
     * package.
     */
    public final WstxInputSource getCurrentInput() {
        return mInput;
    }

    protected final int inputInBuffer() {
        return mInputEnd - mInputPtr;
    }

    protected final int getNext()
        throws XMLStreamException
    {
        if (mInputPtr >= mInputEnd) {
            if (!loadMore()) {
                return -1;
            }
        }
        return (int) mInputBuffer[mInputPtr++];
    }

    /**
     * Similar to {@link #getNext}, but does not advance pointer
     * in input buffer.
     *<p>
     * Note: this method only peeks within current input source;
     * it does not close it and check nested input source (if any).
     * This is necessary when checking keywords, since they can never
     * cross input block boundary.
     */
    protected final int peekNext()
        throws XMLStreamException
    {
        if (mInputPtr >= mInputEnd) {
            if (!loadMoreFromCurrent()) {
                return -1;
            }
        }
        return (int) mInputBuffer[mInputPtr];
    }

    protected final char getNextChar(String errorMsg)
        throws XMLStreamException
    {
        if (mInputPtr >= mInputEnd) {
            loadMore(errorMsg);
        }
        return mInputBuffer[mInputPtr++];
    }

    /**
     * Similar to {@link #getNextChar}, but will not read more characters
     * from parent input source(s) if the current input source doesn't
     * have more content. This is often needed to prevent "runaway" content,
     * such as comments that start in an entity but do not have matching
     * close marker inside entity; XML specification specifically states
     * such markup is not legal.
     */
    protected final char getNextCharFromCurrent(String errorMsg)
        throws XMLStreamException
    {
        if (mInputPtr >= mInputEnd) {
            loadMoreFromCurrent(errorMsg);
        }
        return mInputBuffer[mInputPtr++];
    }

    /**
     * Method that will skip through zero or more white space characters,
     * and return either the character following white space, or -1 to
     * indicate EOF (end of the outermost input source)/
     */
    protected final int getNextAfterWS()
        throws XMLStreamException
    {
        if (mInputPtr >= mInputEnd) {
            if (!loadMore()) {
                return -1;
            }
        }
        char c = mInputBuffer[mInputPtr++];
        while (c <= CHAR_SPACE) {
            // Linefeed?
            if (c == '\n' || c == '\r') {
                skipCRLF(c);
            } else if (c != CHAR_SPACE && c != '\t') {
                throwInvalidSpace(c);
            }
            // Still a white space?
            if (mInputPtr >= mInputEnd) {
                if (!loadMore()) {
                    return -1;
                }
            }
            c = mInputBuffer[mInputPtr++];
        }
        return (int) c;
    }

    protected final char getNextCharAfterWS(String errorMsg)
        throws XMLStreamException
    {
        if (mInputPtr >= mInputEnd) {
            loadMore(errorMsg);
        }

        char c = mInputBuffer[mInputPtr++];
        while (c <= CHAR_SPACE) {
            // Linefeed?
            if (c == '\n' || c == '\r') {
                skipCRLF(c);
            } else if (c != CHAR_SPACE && c != '\t') {
                throwInvalidSpace(c);
            }

            // Still a white space?
            if (mInputPtr >= mInputEnd) {
                loadMore(errorMsg);
            }
            c = mInputBuffer[mInputPtr++];
        }
        return c;
    }

    protected final char getNextInCurrAfterWS(String errorMsg)
        throws XMLStreamException
    {
        return getNextInCurrAfterWS(errorMsg, getNextCharFromCurrent(errorMsg));
    }

    protected final char getNextInCurrAfterWS(String errorMsg, char c)
        throws XMLStreamException
    {
        while (c <= CHAR_SPACE) {
            // Linefeed?
            if (c == '\n' || c == '\r') {
                skipCRLF(c);
            } else if (c != CHAR_SPACE && c != '\t') {
                throwInvalidSpace(c);
            }

            // Still a white space?
            if (mInputPtr >= mInputEnd) {
                loadMoreFromCurrent(errorMsg);
            }
            c = mInputBuffer[mInputPtr++];
        }
        return c;
    }

    /**
     * Method called when a CR has been spotted in input; checks if next
     * char is LF, and if so, skips it. Note that next character has to
     * come from the current input source, to qualify; it can never come
     * from another (nested) input source.
     *
     * @return True, if passed in char is '\r' and next one is '\n'.
     */
    protected final boolean skipCRLF(char c) 
        throws XMLStreamException
    {
        boolean result;

        if (c == '\r' && peekNext() == '\n') {
            ++mInputPtr;
            result = true;
        } else {
            result = false;
        }
        ++mCurrInputRow;
        mCurrInputRowStart = mInputPtr;
        return result;
    }

    protected final void markLF() {
        ++mCurrInputRow;
        mCurrInputRowStart = mInputPtr;
    }

    protected final void markLF(int inputPtr) {
        ++mCurrInputRow;
        mCurrInputRowStart = inputPtr;
    }

    /**
     * Method to push back last character read; can only be called once,
     * that is, no more than one char can be guaranteed to be succesfully
     * returned.
     */
    protected final void pushback() { --mInputPtr; }

    /*
    ///////////////////////////////////////////////////////////
    // Sub-class overridable input handling methods
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method called when an entity has been expanded (new input source
     * has been created). Needs to initialize location information and change
     * active input source.
     *
     * @param entityId Name of the entity being expanded
     */
    protected void initInputSource(WstxInputSource newInput, boolean isExt,
                                   String entityId)
        throws XMLStreamException
    {
        mInput = newInput;
        // Let's make sure new input will be read next time input is needed:
        mInputPtr = 0;
        mInputEnd = 0;
        /* Plus, reset the input location so that'll be accurate for
         * error reporting etc.
         */
        mInputTopDepth = mCurrDepth;
        mInput.initInputLocation(this, mCurrDepth);

        /* 21-Feb-2006, TSa: Linefeeds are NOT normalized when expanding
         *   internal entities (XML, 2.11)
         */
        if (isExt) {
            mNormalizeLFs = true;
        } else {
            mNormalizeLFs = false;
        }
    }

    /**
     * Method that will try to read one or more characters from currently
     * open input sources; closing input sources if necessary.
     *
     * @return true if reading succeeded (or may succeed), false if
     *   we reached EOF.
     */
    protected boolean loadMore()
        throws XMLStreamException
    {
        WstxInputSource input = mInput;
        do {
            /* Need to make sure offsets are properly updated for error
             * reporting purposes, and do this now while previous amounts
             * are still known.
             */
            mCurrInputProcessed += mInputEnd;
            mCurrInputRowStart -= mInputEnd;
            int count;
            try {
                count = input.readInto(this);
                if (count > 0) {
                    return true;
                }
                input.close();
            } catch (IOException ioe) {
                throw constructFromIOE(ioe);
            }
            if (input == mRootInput) {
                /* Note: no need to check entity/input nesting in this
                 * particular case, since it will be handled by higher level
                 * parsing code (results in an unexpected EOF)
                 */
                return false;
            }
            WstxInputSource parent = input.getParent();
            if (parent == null) { // sanity check!
                throwNullParent(input);
            }
            /* 13-Feb-2006, TSa: Ok, do we violate a proper nesting constraints
             *   with this input block closure?
             */
            if (mCurrDepth != input.getScopeId()) {
                handleIncompleteEntityProblem(input);
            }

            mInput = input = parent;
            input.restoreContext(this);
            mInputTopDepth = input.getScopeId();
            /* 21-Feb-2006, TSa: Since linefeed normalization needs to be
             *   suppressed for internal entity expansion, we may need to
             *   change the state...
             */
            if (!mNormalizeLFs) {
                mNormalizeLFs = !input.fromInternalEntity();
            }
            // Maybe there are leftovers from that input in buffer now?
        } while (mInputPtr >= mInputEnd);

        return true;
    }

    protected final boolean loadMore(String errorMsg)
        throws XMLStreamException
    {
        if (!loadMore()) {
            throwUnexpectedEOF(errorMsg);
        }
        return true;
    }

    protected boolean loadMoreFromCurrent()
        throws XMLStreamException
    {
        // Need to update offsets properly
        mCurrInputProcessed += mInputEnd;
        mCurrInputRowStart -= mInputEnd;
        try {
            int count = mInput.readInto(this);
            return (count > 0);
        } catch (IOException ie) {
            throw constructFromIOE(ie);
        }
    }

    protected final boolean loadMoreFromCurrent(String errorMsg)
        throws XMLStreamException
    {
        if (!loadMoreFromCurrent()) {
            throwUnexpectedEOB(errorMsg);
        }
        return true;
    }

    /**
     * Method called to make sure current main-level input buffer has at
     * least specified number of characters available consequtively,
     * without having to call {@link #loadMore}. It can only be called
     * when input comes from main-level buffer; further, call can shift
     * content in input buffer, so caller has to flush any data still
     * pending. In short, caller has to know exactly what it's doing. :-)
     *<p>
     * Note: method does not check for any other input sources than the
     * current one -- if current source can not fulfill the request, a
     * failure is indicated.
     *
     * @return true if there's now enough data; false if not (EOF)
     */
    protected boolean ensureInput(int minAmount)
        throws XMLStreamException
    {
        int currAmount = mInputEnd - mInputPtr;
        if (currAmount >= minAmount) {
            return true;
        }
        try {
            return mInput.readMore(this, minAmount);
        } catch (IOException ie) {
            throw constructFromIOE(ie);
        }
    }

    protected void closeAllInput(boolean force)
        throws XMLStreamException
    {
        WstxInputSource input = mInput;
        while (true) {
            try {
                if (force) {
                    input.closeCompletely();
                } else {
                    input.close();
                }
            } catch (IOException ie) {
                throw constructFromIOE(ie);
            }
            if (input == mRootInput) {
                break;
            }
            WstxInputSource parent = input.getParent();
            if (parent == null) { // sanity check!
                throwNullParent(input);
            }
            mInput = input = parent;
        }
    }

    protected void throwNullParent(WstxInputSource curr)
    {
        throw new IllegalStateException(ErrorConsts.ERR_INTERNAL);
        //throw new IllegalStateException("Internal error: null parent for input source '"+curr+"'; should never occur (should have stopped at root input '"+mRootInput+"').");
    }

    /*
    ///////////////////////////////////////////////////////////
    // Entity resolution
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method that tries to resolve a character entity, or (if caller so
     * specifies), a pre-defined internal entity (lt, gt, amp, apos, quot).
     * It will succeed iff:
     * <ol>
     *  <li>Entity in question is a simple character entity (either one of
     *    5 pre-defined ones, or using decimal/hex notation), AND
     *   <li>
     *  <li>Entity fits completely inside current input buffer.
     *   <li>
     * </ol>
     * If so, character value of entity is returned. Character 0 is returned
     * otherwise; if so, caller needs to do full resolution.
     *<p>
     * Note: On entry we are guaranteed there are at least 3 more characters
     * in this buffer; otherwise we shouldn't be called.
     *
     * @param checkStd If true, will check pre-defined internal entities
     *   (gt, lt, amp, apos, quot); if false, will only check actual
     *   character entities.
     *
     * @return (Valid) character value, if entity is a character reference,
     *   and could be resolved from current input buffer (does not span
     *   buffer boundary); null char (code 0) if not (either non-char
     *   entity, or spans input buffer boundary).
     */
    protected int resolveSimpleEntity(boolean checkStd)
        throws XMLStreamException
    {
        char[] buf = mInputBuffer;
        int ptr = mInputPtr;
        char c = buf[ptr++];

        // Numeric reference?
        if (c == '#') {
            c = buf[ptr++];
            int value = 0;
            int inputLen = mInputEnd;
            if (c == 'x') { // hex
                while (ptr < inputLen) {
                    c = buf[ptr++];
                    if (c == ';') {
                        break;
                    }
                    value = value << 4;
                    if (c <= '9' && c >= '0') {
                        value += (c - '0');
                    } else if (c >= 'a' && c <= 'f') {
                        value += (10 + (c - 'a'));
                    } else if (c >= 'A' && c <= 'F') {
                        value += (10 + (c - 'A'));
                    } else {
                        mInputPtr = ptr; // so error points to correct char
                        throwUnexpectedChar(c, "; expected a hex digit (0-9a-fA-F).");
                    }
                    /* Need to check for overflow; easiest to do right as
                     * it happens...
                     */
                    if (value > MAX_UNICODE_CHAR) {
                        reportUnicodeOverflow();
                    }
                }
            } else { // numeric (decimal)
                while (c != ';') {
                    if (c <= '9' && c >= '0') {
                        value = (value * 10) + (c - '0');
                        // Overflow?
                        if (value > MAX_UNICODE_CHAR) {
                            reportUnicodeOverflow();
                        }
                    } else {
                        mInputPtr = ptr; // so error points to correct char
                        throwUnexpectedChar(c, "; expected a decimal number.");
                    }
                    if (ptr >= inputLen) {
                        break;
                    }
                    c = buf[ptr++];
                }
            }
            /* We get here either if we got it all, OR if we ran out of
             * input in current buffer.
             */
            if (c == ';') { // got the full thing
                mInputPtr = ptr;
                validateChar(value);
                return value;
            }

            /* If we ran out of input, need to just fall back, gets
             * resolved via 'full' resolution mechanism.
             */
        } else if (checkStd) {
            /* Caller may not want to resolve these quite yet...
             * (when it wants separate events for non-char entities)
             */
            if (c == 'a') { // amp or apos?
                c = buf[ptr++];
                
                if (c == 'm') { // amp?
                    if (buf[ptr++] == 'p') {
                        if (ptr < mInputEnd && buf[ptr++] == ';') {
                            mInputPtr = ptr;
                            return '&';
                        }
                    }
                } else if (c == 'p') { // apos?
                    if (buf[ptr++] == 'o') {
                        int len = mInputEnd;
                        if (ptr < len && buf[ptr++] == 's') {
                            if (ptr < len && buf[ptr++] == ';') {
                                mInputPtr = ptr;
                                return '\'';
                            }
                        }
                    }
                }
            } else if (c == 'g') { // gt?
                if (buf[ptr++] == 't' && buf[ptr++] == ';') {
                    mInputPtr = ptr;
                    return '>';
                }
            } else if (c == 'l') { // lt?
                if (buf[ptr++] == 't' && buf[ptr++] == ';') {
                    mInputPtr = ptr;
                    return '<';
                }
            } else if (c == 'q') { // quot?
                if (buf[ptr++] == 'u' && buf[ptr++] == 'o') {
                    int len = mInputEnd;
                    if (ptr < len && buf[ptr++] == 't') {
                        if (ptr < len && buf[ptr++] == ';') {
                            mInputPtr = ptr;
                            return '"';
                        }
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Method called to resolve character entities, and only character
     * entities (except that pre-defined char entities -- amp, apos, lt,
     * gt, quote -- MAY be "char entities" in this sense, depending on
     * arguments).
     * Otherwise it is to return the null char; if so,
     * the input pointer will point to the same point as when method
     * entered (char after ampersand), plus the ampersand itself is
     * guaranteed to be in the input buffer (so caller can just push it
     * back if necessary).
     *<p>
     * Most often this method is called when reader is not to expand
     * non-char entities automatically, but to return them as separate
     * events.
     *<p>
     * Main complication here is that we need to do 5-char lookahead. This
     * is problematic if chars are on input buffer boundary. This is ok
     * for the root level input buffer, but not for some nested buffers.
     * However, according to XML specs, such split entities are actually
     * illegal... so we can throw an exception in those cases.
     *
     * @param checkStd If true, will check pre-defined internal entities
     *   (gt, lt, amp, apos, quot) as character entities; if false, will only
     *   check actual 'real' character entities.
     *
     * @return (Valid) character value, if entity is a character reference,
     *   and could be resolved from current input buffer (does not span
     *   buffer boundary); null char (code 0) if not (either non-char
     *   entity, or spans input buffer boundary).
     */
    protected int resolveCharOnlyEntity(boolean checkStd)
        throws XMLStreamException
    {
        //int avail = inputInBuffer();
        int avail = mInputEnd - mInputPtr;
        if (avail < 6) {
            // split entity, or buffer boundary
            /* Don't want to lose leading '&' (in case we can not expand
             * the entity), so let's push it back first
             */
            --mInputPtr;
            /* Shortest valid reference would be 3 chars ('&a;'); which
             * would only be legal from an expanded entity...
             */
            if (!ensureInput(6)) {
                avail = inputInBuffer();
                if (avail < 3) {
                    throwUnexpectedEOF(SUFFIX_IN_ENTITY_REF);
                }
            } else {
                avail = 6;
            }
            // ... and now we can move pointer back as well:
            ++mInputPtr;
        }

        /* Ok, now we have one more character to check, and that's enough
         * to determine type decisively.
         */
        char c = mInputBuffer[mInputPtr];

        // A char reference?
        if (c == '#') { // yup
            ++mInputPtr;
            return resolveCharEnt(null);
        }

        // nope... except may be a pre-def?
        if (checkStd) {
            if (c == 'a') {
                char d = mInputBuffer[mInputPtr+1];
                if (d == 'm') {
                    if (avail >= 4
                        && mInputBuffer[mInputPtr+2] == 'p'
                        && mInputBuffer[mInputPtr+3] == ';') {
                        mInputPtr += 4;
                        return '&';
                    }
                } else if (d == 'p') {
                    if (avail >= 5
                        && mInputBuffer[mInputPtr+2] == 'o'
                        && mInputBuffer[mInputPtr+3] == 's'
                        && mInputBuffer[mInputPtr+4] == ';') {
                        mInputPtr += 5;
                        return '\'';
                    }
                }
            } else if (c == 'l') {
                if (avail >= 3
                    && mInputBuffer[mInputPtr+1] == 't'
                    && mInputBuffer[mInputPtr+2] == ';') {
                    mInputPtr += 3;
                    return '<';
                }
            } else if (c == 'g') {
                if (avail >= 3
                    && mInputBuffer[mInputPtr+1] == 't'
                    && mInputBuffer[mInputPtr+2] == ';') {
                    mInputPtr += 3;
                    return '>';
                }
            } else if (c == 'q') {
                if (avail >= 5
                    && mInputBuffer[mInputPtr+1] == 'u'
                    && mInputBuffer[mInputPtr+2] == 'o'
                    && mInputBuffer[mInputPtr+3] == 't'
                    && mInputBuffer[mInputPtr+4] == ';') {
                    mInputPtr += 5;
                    return '"';
                }
            }
        }
        return 0;
    }

    /**
     * Reverse of {@link #resolveCharOnlyEntity}; will only resolve entity
     * if it is NOT a character entity (or pre-defined 'generic' entity;
     * amp, apos, lt, gt or quot). Only used in cases where entities
     * are to be separately returned unexpanded (in non-entity-replacing
     * mode); which means it's never called from dtd handler.
     */
    protected EntityDecl resolveNonCharEntity()
        throws XMLStreamException
    {
        //int avail = inputInBuffer();
        int avail = mInputEnd - mInputPtr;
        if (avail < 6) {
            // split entity, or buffer boundary
            /* Don't want to lose leading '&' (in case we can not expand
             * the entity), so let's push it back first
             */
            --mInputPtr;

            /* Shortest valid reference would be 3 chars ('&a;'); which
             * would only be legal from an expanded entity...
             */
            if (!ensureInput(6)) {
                avail = inputInBuffer();
                if (avail < 3) {
                    throwUnexpectedEOF(SUFFIX_IN_ENTITY_REF);
                }
            } else {
                avail = 6;
            }
            // ... and now we can move pointer back as well:
            ++mInputPtr;
        }

        // We don't care about char entities:
        char c = mInputBuffer[mInputPtr];
        if (c == '#') {
            return null;
        }

        /* 19-Aug-2004, TSa: Need special handling for pre-defined
         *   entities; they are not counted as 'real' general parsed
         *   entities, but more as character entities...
         */

        // have chars at least up to mInputPtr+4 by now
        if (c == 'a') {
            char d = mInputBuffer[mInputPtr+1];
            if (d == 'm') {
                if (avail >= 4
                    && mInputBuffer[mInputPtr+2] == 'p'
                    && mInputBuffer[mInputPtr+3] == ';') {
                    // If not automatically expanding:
                    //return sEntityAmp;
                    // mInputPtr += 4;
                    return null;
                }
            } else if (d == 'p') {
                if (avail >= 5
                    && mInputBuffer[mInputPtr+2] == 'o'
                    && mInputBuffer[mInputPtr+3] == 's'
                    && mInputBuffer[mInputPtr+4] == ';') {
                    return null;
                }
            }
        } else if (c == 'l') {
            if (avail >= 3
                && mInputBuffer[mInputPtr+1] == 't'
                && mInputBuffer[mInputPtr+2] == ';') {
                return null;
            }
        } else if (c == 'g') {
            if (avail >= 3
                && mInputBuffer[mInputPtr+1] == 't'
                && mInputBuffer[mInputPtr+2] == ';') {
                return null;
            }
        } else if (c == 'q') {
            if (avail >= 5
                && mInputBuffer[mInputPtr+1] == 'u'
                && mInputBuffer[mInputPtr+2] == 'o'
                && mInputBuffer[mInputPtr+3] == 't'
                && mInputBuffer[mInputPtr+4] == ';') {
                return null;
            }
        }

        // Otherwise, let's just parse in generic way:
        ++mInputPtr; // since we already read the first letter
        String id = parseEntityName(c);
        mCurrName = id;

        return findEntity(id, null);
    }

    /**
     * Method that does full resolution of an entity reference, be it
     * character entity, internal entity or external entity, including
     * updating of input buffers, and depending on whether result is
     * a character entity (or one of 5 pre-defined entities), returns
     * char in question, or null character (code 0) to indicate it had
     * to change input source.
     *
     * @param allowExt If true, is allowed to expand external entities
     *   (expanding text); if false, is not (expanding attribute value).
     *
     * @return Either single-character replacement (which is NOT to be
     *    reparsed), or null char (0) to indicate expansion is done via
     *    input source.
     */
    protected int fullyResolveEntity(boolean allowExt)
        throws XMLStreamException
    {
        char c = getNextCharFromCurrent(SUFFIX_IN_ENTITY_REF);
        // Do we have a (numeric) character entity reference?
        if (c == '#') { // numeric
            final StringBuffer originalSurface = new StringBuffer("#");
            int ch = resolveCharEnt(originalSurface);
            if (mCfgTreatCharRefsAsEntities) {
                final char[] originalChars = new char[originalSurface.length()];
                originalSurface.getChars(0, originalSurface.length(), originalChars, 0);
                mCurrEntity = getIntEntity(ch, originalChars);
                return 0;
            }
            return ch;
        }

        String id = parseEntityName(c);
 
        // Perhaps we have a pre-defined char reference?
        c = id.charAt(0);
        /*
         * 16-May-2004, TSa: Should custom entities (or ones defined in int/ext subset) override
         * pre-defined settings for these?
         */
        char d = CHAR_NULL;
        if (c == 'a') { // amp or apos?
            if (id.equals("amp")) {
                d = '&';
            } else if (id.equals("apos")) {
                d = '\'';
            }
        } else if (c == 'g') { // gt?
            if (id.length() == 2 && id.charAt(1) == 't') {
                d = '>';
            }
        } else if (c == 'l') { // lt?
            if (id.length() == 2 && id.charAt(1) == 't') {
                d = '<';
            }
        } else if (c == 'q') { // quot?
            if (id.equals("quot")) {
                d = '"';
            }
        }

        if (d != CHAR_NULL) {
            if (mCfgTreatCharRefsAsEntities) {
                final char[] originalChars = new char[id.length()];
                id.getChars(0, id.length(), originalChars, 0);
                mCurrEntity = getIntEntity(d, originalChars);
                return 0;
            }
            return d;
        }

        final EntityDecl e = expandEntity(id, allowExt, null);
        if (mCfgTreatCharRefsAsEntities) {
            mCurrEntity = e;
        }
        return 0;
    }

    /**
     * Returns an entity (possibly from cache) for the argument character using the encoded
     * representation in mInputBuffer[entityStartPos ... mInputPtr-1].
     */
    protected EntityDecl getIntEntity(int ch, final char[] originalChars)
    {
        String cacheKey = new String(originalChars);

        IntEntity entity = (IntEntity) mCachedEntities.get(cacheKey);
        if (entity == null) {
            String repl;
            if (ch <= 0xFFFF) {
                repl = Character.toString((char) ch);
            } else {
                StringBuffer sb = new StringBuffer(2);
                ch -= 0x10000;
                sb.append((char) ((ch >> 10)  + 0xD800));
                sb.append((char) ((ch & 0x3FF)  + 0xDC00));
                repl = sb.toString();
            }
            entity = IntEntity.create(new String(originalChars), repl);
            mCachedEntities.put(cacheKey, entity);
        }
        return entity;
    }


    /**
     * Helper method that will try to expand a parsed entity (parameter or
     * generic entity).
     *<p>
     * note: called by sub-classes (dtd parser), needs to be protected.
     *
     * @param id Name of the entity being expanded 
     * @param allowExt Whether external entities can be expanded or not; if
     *   not, and the entity to expand would be external one, an exception
     *   will be thrown
     */
    protected EntityDecl expandEntity(String id, boolean allowExt,
                                      Object extraArg)
        throws XMLStreamException
    {
        mCurrName = id;

        EntityDecl ed = findEntity(id, extraArg);

        if (ed == null) {
            /* 30-Sep-2005, TSa: As per [WSTX-5], let's only throw exception
             *   if we have to resolve it (otherwise it's just best-effort, 
             *   and null is ok)
             */
            /* 02-Oct-2005, TSa: Plus, [WSTX-4] adds "undeclared entity
             *    resolver"
             */
            if (mCfgReplaceEntities) {
                mCurrEntity = expandUnresolvedEntity(id);
            }
            return null;
        }
        
        if (!mCfgTreatCharRefsAsEntities || this instanceof MinimalDTDReader) {
            expandEntity(ed, allowExt);
        }
        
        return ed;
    }

    /**
     *
     *<p>
     * note: defined as private for documentation, ie. it's just called
     * from within this class (not sub-classes), from one specific method
     * (see above)
     *
     * @param ed Entity to be expanded
     * @param allowExt Whether external entities are allowed or not.
     */
    private void expandEntity(EntityDecl ed, boolean allowExt)
        throws XMLStreamException
    {
        String id = ed.getName();

        /* Very first thing; we can immediately check if expanding
         * this entity would result in infinite recursion:
         */
        if (mInput.isOrIsExpandedFrom(id)) {
            throwRecursionError(id);
        }

        /* Should not refer unparsed entities from attribute values
         * or text content (except via notation mechanism, but that's
         * not parsed here)
         */
        if (!ed.isParsed()) {
            throwParseError("Illegal reference to unparsed external entity \"{0}\"", id, null);
        }

        // 28-Jun-2004, TSa: Do we support external entity expansion?
        boolean isExt = ed.isExternal();
        if (isExt) {
            if (!allowExt) { // never ok in attribute value...
                throwParseError("Encountered a reference to external parsed entity \"{0}\" when expanding attribute value: not legal as per XML 1.0/1.1 #3.1", id, null);
            }
            if (!mConfig.willSupportExternalEntities()) {
                throwParseError("Encountered a reference to external entity \"{0}\", but stream reader has feature \"{1}\" disabled",
                                id, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES);
            }
        }

        // First, let's give current context chance to save its stuff
        WstxInputSource oldInput = mInput;
        oldInput.saveContext(this);
        WstxInputSource newInput = null;
        try {
            newInput = ed.expand(oldInput, mEntityResolver, mConfig, mDocXmlVersion);
        } catch (FileNotFoundException fex) {
            /* Let's catch and rethrow this just so we get more meaningful
             * description (with input source position etc)
             */
            throwParseError("(was {0}) {1}", fex.getClass().getName(), fex.getMessage());
        } catch (IOException ioe) {
            throw constructFromIOE(ioe);
        }
        /* And then we'll need to make sure new input comes from the new
         * input source
         */
        initInputSource(newInput, isExt, id);
    }

    /**
     *<p>
     * note: only called from the local expandEntity() method
     */
    private EntityDecl expandUnresolvedEntity(String id)
        throws XMLStreamException
    {
        XMLResolver resolver = mConfig.getUndeclaredEntityResolver();
        if (resolver != null) {
            /* Ok, we can check for recursion here; but let's only do that
             * if there is any chance that it might get resolved by
             * the special resolver (it must have been resolved this way
             * earlier, too...)
             */
            if (mInput.isOrIsExpandedFrom(id)) {
                throwRecursionError(id);
            }

            WstxInputSource oldInput = mInput;
            oldInput.saveContext(this);
            // null, null -> no public or system ids
            int xmlVersion = mDocXmlVersion;
            // 05-Feb-2006, TSa: If xmlVersion not explicitly known, defaults to 1.0
            if (xmlVersion == XmlConsts.XML_V_UNKNOWN) {
                xmlVersion = XmlConsts.XML_V_10;
            }
            WstxInputSource newInput;
            try {
                newInput = DefaultInputResolver.resolveEntityUsing
                    (oldInput, id, null, null, resolver, mConfig, xmlVersion);
                if (mCfgTreatCharRefsAsEntities) {
                    return new IntEntity(WstxInputLocation.getEmptyLocation(), newInput.getEntityId(),
                            newInput.getSource(), new char[]{}, WstxInputLocation.getEmptyLocation());
                }
            } catch (IOException ioe) {
                throw constructFromIOE(ioe);
            }
            if (newInput != null) {
                // true -> is external
                initInputSource(newInput, true, id);
                return null;
            }
        }
        handleUndeclaredEntity(id);
        return null;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Abstract methods for sub-classes to implement
    ///////////////////////////////////////////////////////////
     */

    /**
     * Abstract method for sub-classes to implement, for finding
     * a declared general or parsed entity.
     *
     * @param id Identifier of the entity to find
     * @param arg Optional argument passed from caller; needed by DTD
     *    reader.
     */
    protected abstract EntityDecl findEntity(String id, Object arg)
        throws XMLStreamException;

    /**
     * This method gets called if a declaration for an entity was not
     * found in entity expanding mode (enabled by default for xml reader,
     * always enabled for dtd reader).
     */
    protected abstract void handleUndeclaredEntity(String id)
        throws XMLStreamException;

    protected abstract void handleIncompleteEntityProblem(WstxInputSource closing)
        throws XMLStreamException;

    /*
    ///////////////////////////////////////////////////////////
    // Basic tokenization
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method that will parse name token (roughly equivalent to XML specs;
     * although bit lenier for more efficient handling); either uri prefix,
     * or local name.
     *<p>
     * Much of complexity in this method has to do with the intention to 
     * try to avoid any character copies. In this optimal case algorithm
     * would be fairly simple. However, this only works if all data is
     * already in input buffer... if not, copy has to be made halfway
     * through parsing, and that complicates things.
     *<p>
     * One thing to note is that String returned has been canonicalized
     * and (if necessary) added to symbol table. It can thus be compared
     * against other such (usually id) Strings, with simple equality operator.
     *
     * @param c First character of the name; not yet checked for validity
     *
     * @return Canonicalized name String (which may have length 0, if
     *    EOF or non-name-start char encountered)
     */
    protected String parseLocalName(char c)
        throws XMLStreamException
    {
        /* Has to start with letter, or '_' (etc); we won't allow ':' as that
         * is taken as namespace separator; no use trying to optimize
         * heavily as it's 98% likely it is a valid char...
         */
        if (!isNameStartChar(c)) {
            if (c == ':') {
                throwUnexpectedChar(c, " (missing namespace prefix?)");
            }
            throwUnexpectedChar(c, " (expected a name start character)");
        }

        int ptr = mInputPtr;
        int hash = (int) c;
        final int inputLen = mInputEnd;
        int startPtr = ptr-1; // already read previous char
        final char[] inputBuf = mInputBuffer;

        /* After which there may be zero or more name chars
         * we have to consider
         */
        while (true) {
            if (ptr >= inputLen) {
                /* Ok, identifier may continue past buffer end, need
                 * to continue with part 2 (separate method, as this is
                 * not as common as having it all in buffer)
                 */
                mInputPtr = ptr;
                return parseLocalName2(startPtr, hash);
            }
            // Ok, we have the char... is it a name char?
            c = inputBuf[ptr];
            if (c < CHAR_LOWEST_LEGAL_LOCALNAME_CHAR) {
                break;
            }
            if (!isNameChar(c)) {
                break;
            }
            hash = (hash * 31) + (int) c;
            ++ptr;
        }
        mInputPtr = ptr;
        return mSymbols.findSymbol(mInputBuffer, startPtr, ptr - startPtr, hash);
    }

    /**
     * Second part of name token parsing; called when name can continue
     * past input buffer end (so only part was read before calling this
     * method to read the rest).
     *<p>
     * Note that this isn't heavily optimized, on assumption it's not
     * called very often.
     */
    protected String parseLocalName2(int start, int hash)
        throws XMLStreamException
    {
        int ptr = mInputEnd - start;
        // Let's assume fairly short names
        char[] outBuf = getNameBuffer(ptr+8);

        if (ptr > 0) {
            System.arraycopy(mInputBuffer, start, outBuf, 0, ptr);
        }

        int outLen = outBuf.length;
        while (true) {
            // note: names can not cross input block (entity) boundaries...
            if (mInputPtr >= mInputEnd) {
                if (!loadMoreFromCurrent()) {
                    break;
                }
            }
            char c = mInputBuffer[mInputPtr];
            if (c < CHAR_LOWEST_LEGAL_LOCALNAME_CHAR) {
                break;
            }
            if (!isNameChar(c)) {
                break;
            }
            ++mInputPtr;
            if (ptr >= outLen) {
                mNameBuffer = outBuf = expandBy50Pct(outBuf);
                outLen = outBuf.length;
            }
            outBuf[ptr++] = c;
            hash = (hash * 31) + (int) c;
        }
        // Still need to canonicalize the name:
        return mSymbols.findSymbol(outBuf, 0, ptr, hash);
    }

    /**
     * Method that will parse 'full' name token; what full means depends on
     * whether reader is namespace aware or not. If it is, full name means
     * local name with no namespace prefix (PI target, entity/notation name);
     * if not, name can contain arbitrary number of colons. Note that
     * element and attribute names are NOT parsed here, so actual namespace
     * prefix separation can be handled properly there.
     *<p>
     * Similar to {@link #parseLocalName}, much of complexity stems from
     * trying to avoid copying name characters from input buffer.
     *<p>
     * Note that returned String will be canonicalized, similar to
     * {@link #parseLocalName}, but without separating prefix/local name.
      *
     * @return Canonicalized name String (which may have length 0, if
     *    EOF or non-name-start char encountered)
     */
    protected String parseFullName()
        throws XMLStreamException
    {
        if (mInputPtr >= mInputEnd) {
            loadMoreFromCurrent();
        }
        return parseFullName(mInputBuffer[mInputPtr++]);
    }

    protected String parseFullName(char c)
        throws XMLStreamException
    {
        // First char has special handling:
        if (!isNameStartChar(c)) {
            if (c == ':') { // no name.... generally an error:
                if (mCfgNsEnabled) {
                    throwNsColonException(parseFNameForError());
                }
                // Ok, that's fine actually
            } else {
                if (c <= CHAR_SPACE) {
                    throwUnexpectedChar(c, " (missing name?)");
                }
                throwUnexpectedChar(c, " (expected a name start character)");
            }
        }

        int ptr = mInputPtr;
        int hash = (int) c;
        int inputLen = mInputEnd;
        int startPtr = ptr-1; // to account for the first char

        /* After which there may be zero or more name chars
         * we have to consider
         */
        while (true) {
            if (ptr >= inputLen) {
                /* Ok, identifier may continue past buffer end, need
                 * to continue with part 2 (separate method, as this is
                 * not as common as having it all in buffer)
                 */
                mInputPtr = ptr;
                return parseFullName2(startPtr, hash);
            }
            c = mInputBuffer[ptr];
            if (c == ':') { // colon only allowed in non-NS mode
                if (mCfgNsEnabled) {
                    mInputPtr = ptr;
                    throwNsColonException(new String(mInputBuffer, startPtr, ptr - startPtr) + parseFNameForError());
                }
            } else {
                if (c < CHAR_LOWEST_LEGAL_LOCALNAME_CHAR) {
                    break;
                }
                if (!isNameChar(c)) {
                    break;
                }
            }
            hash = (hash * 31) + (int) c;
            ++ptr;
        }
        mInputPtr = ptr;
        return mSymbols.findSymbol(mInputBuffer, startPtr, ptr - startPtr, hash);
    }

    protected String parseFullName2(int start, int hash)
        throws XMLStreamException
    {
        int ptr = mInputEnd - start;
        // Let's assume fairly short names
        char[] outBuf = getNameBuffer(ptr+8);

        if (ptr > 0) {
            System.arraycopy(mInputBuffer, start, outBuf, 0, ptr);
        }

        int outLen = outBuf.length;
        while (true) {
            /* 06-Sep-2004, TSa: Name tokens are not allowed to continue
             *   past entity expansion ranges... that is, all characters
             *   have to come from the same input source. Thus, let's only
             *   load things from same input level
             */
            if (mInputPtr >= mInputEnd) {
                if (!loadMoreFromCurrent()) {
                    break;
                }
            }
            char c = mInputBuffer[mInputPtr];
            if (c == ':') { // colon only allowed in non-NS mode
                if (mCfgNsEnabled) {
                    throwNsColonException(new String(outBuf, 0, ptr) + c + parseFNameForError());
                }
            } else if (c < CHAR_LOWEST_LEGAL_LOCALNAME_CHAR) {
                break;
            } else if (!isNameChar(c)) {
                break;
            }
            ++mInputPtr;

            if (ptr >= outLen) {
                mNameBuffer = outBuf = expandBy50Pct(outBuf);
                outLen = outBuf.length;
            }
            outBuf[ptr++] = c;
            hash = (hash * 31) + (int) c;
        }

        // Still need to canonicalize the name:
        return mSymbols.findSymbol(outBuf, 0, ptr, hash);
    }

    /**
     * Method called to read in full name, including unlimited number of
     * namespace separators (':'), for the purpose of displaying name in
     * an error message. Won't do any further validations, and parsing
     * is not optimized: main need is just to get more meaningful error
     * messages.
     */
    protected String parseFNameForError()
        throws XMLStreamException
    {
        StringBuffer sb = new StringBuffer(100);
        while (true) {
            char c;

            if (mInputPtr < mInputEnd) {
                c = mInputBuffer[mInputPtr++];
            } else { // can't error here, so let's accept EOF for now:
                int i = getNext();
                if (i < 0) {
                    break;
                }
                c = (char) i;
            }
            if (c != ':' && !isNameChar(c)) {
                --mInputPtr;
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    protected final String parseEntityName(char c)
        throws XMLStreamException
    {
        String id = parseFullName(c);
        // Needs to be followed by a semi-colon, too.. from same input source:
        if (mInputPtr >= mInputEnd) {
            if (!loadMoreFromCurrent()) {
                throwParseError("Missing semicolon after reference for entity \"{0}\"", id, null);
            }
        }
        c = mInputBuffer[mInputPtr++];
        if (c != ';') {
            throwUnexpectedChar(c, "; expected a semi-colon after the reference for entity '"+id+"'");
        }
        return id;
    }
    
    /**
     * Note: does not check for number of colons, amongst other things.
     * Main idea is to skip through what superficially seems like a valid
     * id, nothing more. This is only done when really skipping through
     * something we do not care about at all: not even whether names/ids
     * would be valid (for example, when ignoring internal DTD subset).
     *
     * @return Length of skipped name.
     */
    protected int skipFullName(char c)
        throws XMLStreamException
    {
        if (!isNameStartChar(c)) {
            --mInputPtr;
            return 0;
        }

        /* After which there may be zero or more name chars
         * we have to consider
         */
        int count = 1;
        while (true) {
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextChar(SUFFIX_EOF_EXP_NAME);
            if (c != ':' && !isNameChar(c)) {
                break;
            }
            ++count;
        }
        return count;
    }

    /**
     * Simple parsing method that parses system ids, which are generally
     * used in entities (from DOCTYPE declaration to internal/external
     * subsets).
     *<p>
     * NOTE: returned String is not canonicalized, on assumption that
     * external ids may be longish, and are not shared all that often, as
     * they are generally just used for resolving paths, if anything.
     *<br />
     * Also note that this method is not heavily optimized, as it's not
     * likely to be a bottleneck for parsing.
     */
    protected final String parseSystemId(char quoteChar, boolean convertLFs,
                                         String errorMsg)
        throws XMLStreamException
    {
        char[] buf = getNameBuffer(-1);
        int ptr = 0;

        while (true) {
            char c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextChar(errorMsg);
            if (c == quoteChar) {
                break;
            }
            /* ??? 14-Jun-2004, TSa: Should we normalize linefeeds or not?
             *   It seems like we should, for all input... so that's the way it
             *   works.
             */
            if (c == '\n') {
                markLF();
            } else if (c == '\r') {
                if (peekNext() == '\n') {
                    ++mInputPtr;
                    if (!convertLFs) {
                        /* The only tricky thing; need to preserve 2-char LF; need to
                         * output one char from here, then can fall back to default:
                         */
                        if (ptr >= buf.length) {
                            buf = expandBy50Pct(buf);
                        }
                        buf[ptr++] = '\r';
                    }
                    c = '\n';
                } else if (convertLFs) {
                    c = '\n';
                }
            }

            // Other than that, let's just append it:
            if (ptr >= buf.length) {
                buf = expandBy50Pct(buf);
            }
            buf[ptr++] = c;
        }

        return (ptr == 0) ? "" : new String(buf, 0, ptr);
    }

    /**
     * Simple parsing method that parses system ids, which are generally
     * used in entities (from DOCTYPE declaration to internal/external
     * subsets).
     *<p>
     * As per xml specs, the contents are actually normalized.
     *<p>
     * NOTE: returned String is not canonicalized, on assumption that
     * external ids may be longish, and are not shared all that often, as
     * they are generally just used for resolving paths, if anything.
     *<br />
     * Also note that this method is not heavily optimized, as it's not
     * likely to be a bottleneck for parsing.
     */
    protected final String parsePublicId(char quoteChar, String errorMsg)
        throws XMLStreamException
    {
        char[] buf = getNameBuffer(-1);
        int ptr = 0;
        boolean spaceToAdd = false;

        while (true) {
            char c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextChar(errorMsg);
            if (c == quoteChar) {
                break;
            }
            if (c == '\n') {
                markLF();
                spaceToAdd = true;
                continue;
            } else if (c == '\r') {
                if (peekNext() == '\n') {
                    ++mInputPtr;
                }
                spaceToAdd = true;
                continue;
            } else if (c == CHAR_SPACE) {
                spaceToAdd = true;
                continue;
            } else {
                // Verify it's a legal pubid char (see XML spec, #13, from 2.3)
                if ((c >= VALID_PUBID_CHAR_COUNT)
                    || sPubidValidity[c] != PUBID_CHAR_VALID_B) {
                    throwUnexpectedChar(c, " in public identifier");
                }
            }
        
            // Other than that, let's just append it:
            if (ptr >= buf.length) {
                buf = expandBy50Pct(buf);
            }
            /* Space-normalization means scrapping leading and trailing
             * white space, and coalescing remaining ws into single spaces.
             */
            if (spaceToAdd) { // pending white space to add?
                if (c == CHAR_SPACE) { // still a space; let's skip
                    continue;
                }
                /* ok: if we have non-space, we'll either forget about
                 * space(s) (if nothing has been output, ie. leading space),
                 * or output a single space (in-between non-white space)
                 */
                spaceToAdd = false;
                if (ptr > 0) {
                    buf[ptr++] = CHAR_SPACE;
                    if (ptr >= buf.length) {
                        buf = expandBy50Pct(buf);
                    }
                }
            }
            buf[ptr++] = c;
        }
      
        return (ptr == 0) ? "" : new String(buf, 0, ptr);
    }

    protected final void parseUntil(TextBuffer tb, char endChar, boolean convertLFs,
                                    String errorMsg)
        throws XMLStreamException
    {
        // Let's first ensure we have some data in there...
        if (mInputPtr >= mInputEnd) {
            loadMore(errorMsg);
        }
        while (true) {
            // Let's loop consequtive 'easy' spans:
            char[] inputBuf = mInputBuffer;
            int inputLen = mInputEnd;
            int ptr = mInputPtr;
            int startPtr = ptr;
            while (ptr < inputLen) {
                char c = inputBuf[ptr++];
                if (c == endChar) {
                    int thisLen = ptr - startPtr - 1;
                    if (thisLen > 0) {
                        tb.append(inputBuf, startPtr, thisLen);
                    }
                    mInputPtr = ptr;
                    return;
                }
                if (c == '\n') {
                    mInputPtr = ptr; // markLF() requires this
                    markLF();
                } else if (c == '\r') {
                    if (!convertLFs && ptr < inputLen) {
                        if (inputBuf[ptr] == '\n') {
                            ++ptr;
                        }
                        mInputPtr = ptr;
                        markLF();
                    } else {
                        int thisLen = ptr - startPtr - 1;
                        if (thisLen > 0) {
                            tb.append(inputBuf, startPtr, thisLen);
                        }
                        mInputPtr = ptr;
                        c = getNextChar(errorMsg);
                        if (c != '\n') {
                            --mInputPtr; // pusback
                            tb.append(convertLFs ? '\n' : '\r');
                        } else {
                            if (convertLFs) {
                                tb.append('\n');
                            } else {
                                tb.append('\r');
                                tb.append('\n');
                            }
                        }
                        startPtr = ptr = mInputPtr;
                        markLF();
                    }
                }
            }
            int thisLen = ptr - startPtr;
            if (thisLen > 0) {
                tb.append(inputBuf, startPtr, thisLen);
            }
            loadMore(errorMsg);
            startPtr = ptr = mInputPtr;
            inputBuf = mInputBuffer;
            inputLen = mInputEnd;
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////
     */

    private int resolveCharEnt(StringBuffer originalCharacters)
        throws XMLStreamException
    {
        int value = 0;
        char c = getNextChar(SUFFIX_IN_ENTITY_REF);
        
        if (originalCharacters != null) {
            originalCharacters.append(c);
        }
        
        if (c == 'x') { // hex
            while (true) {
                c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                    : getNextCharFromCurrent(SUFFIX_IN_ENTITY_REF);
                if (c == ';') {
                    break;
                }
                
                if (originalCharacters != null) {
                    originalCharacters.append(c);
                }
                value = value << 4;
                if (c <= '9' && c >= '0') {
                    value += (c - '0');
                } else if (c >= 'a' && c <= 'f') {
                    value += 10 + (c - 'a');
                } else if (c >= 'A' && c <= 'F') {
                    value += 10 + (c - 'A');
                } else {
                    throwUnexpectedChar(c, "; expected a hex digit (0-9a-fA-F).");
                }
                // Overflow?
                if (value > MAX_UNICODE_CHAR) {
                    reportUnicodeOverflow();
                }
            }
        } else { // numeric (decimal)
            while (c != ';') {
                if (c <= '9' && c >= '0') {
                    value = (value * 10) + (c - '0');
                    // Overflow?
                    if (value > MAX_UNICODE_CHAR) {
                        reportUnicodeOverflow();
                    }
                } else {
                    throwUnexpectedChar(c, "; expected a decimal number.");
                }
                c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                    : getNextCharFromCurrent(SUFFIX_IN_ENTITY_REF);
                
                if (originalCharacters != null && c != ';') {
                    originalCharacters.append(c);
                }
            }
        }
        validateChar(value);
        return value;
    }

    /**
     * Method that will verify that expanded Unicode codepoint is a valid
     * XML content character.
     */
    private final void validateChar(int value)
        throws XMLStreamException
    {
        /* 24-Jan-2006, TSa: Ok, "high" Unicode chars are problematic,
         *   need to be reported by a surrogate pair..
         */
        if (value >= 0xD800) {
            if (value < 0xE000) { // no surrogates via entity expansion
                reportIllegalChar(value);
            }
            if (value > 0xFFFF) {
                // Within valid range at all?
                if (value > MAX_UNICODE_CHAR) {
                    reportUnicodeOverflow();
                }
            } else if (value >= 0xFFFE) { // 0xFFFE and 0xFFFF are illegal too
                reportIllegalChar(value);
            }
            // Ok, fine as is
        } else if (value < 32) {
            if (value == 0) {
                throwParseError("Invalid character reference: null character not allowed in XML content.");
            }
            // XML 1.1 allows most other chars; 1.0 does not:
            if (!mXml10AllowAllEscapedChars) {
                if (!mXml11 &&
                    (value != 0x9 && value != 0xA && value != 0xD)) {
                    reportIllegalChar(value);
                }
            }
        }
    }

    protected final char[] getNameBuffer(int minSize)
    {
        char[] buf = mNameBuffer;
        
        if (buf == null) {
            mNameBuffer = buf = new char[(minSize > 48) ? (minSize+16) : 64];
        } else if (minSize >= buf.length) { // let's allow one char extra...
            int len = buf.length;
            len += (len >> 1); // grow by 50%
            mNameBuffer = buf = new char[(minSize >= len) ? (minSize+16) : len];
        }
        return buf;
    }
    
    protected final char[] expandBy50Pct(char[] buf)
    {
        int len = buf.length;
        char[] newBuf = new char[len + (len >> 1)];
        System.arraycopy(buf, 0, newBuf, 0, len);
        return newBuf;
    }

    /**
     * Method called to throw an exception indicating that a name that
     * should not be namespace-qualified (PI target, entity/notation name)
     * is one, and reader is namespace aware.
     */
    private void throwNsColonException(String name)
        throws XMLStreamException
    {
        throwParseError("Illegal name \"{0}\" (PI target, entity/notation name): can not contain a colon (XML Namespaces 1.0#6)", name, null);
    }

    private void throwRecursionError(String entityName)
        throws XMLStreamException
    {
        throwParseError("Illegal entity expansion: entity \"{0}\" expands itself recursively.", entityName, null);
    }

    private void reportUnicodeOverflow()
        throws XMLStreamException
    {
        throwParseError("Illegal character entity: value higher than max allowed (0x{0})", Integer.toHexString(MAX_UNICODE_CHAR), null);
    }

    private void reportIllegalChar(int value)
        throws XMLStreamException
    {
        throwParseError("Illegal character entity: expansion character (code 0x{0}", Integer.toHexString(value), null);
    }
}
