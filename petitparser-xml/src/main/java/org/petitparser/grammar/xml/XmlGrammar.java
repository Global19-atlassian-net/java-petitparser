package org.petitparser.grammar.xml;

import org.petitparser.tools.CompositeParser;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.petitparser.parser.primitive.CharacterParser.any;
import static org.petitparser.parser.primitive.CharacterParser.pattern;
import static org.petitparser.parser.primitive.CharacterParser.whitespace;
import static org.petitparser.parser.primitive.StringParser.of;

/**
 * XML grammar definition.
 */
public abstract class XmlGrammar<TNode, TName> extends CompositeParser {

  protected static final String NAME_START_CHARS = ":A-Z_a-z\u00C0-\u00D6\u00D8-\u00F6"
      + "\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF"
      + "\u3001\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD";
  protected static final String NAME_CHARS = "-.0-9\u00B7\u0300-\u036F\u203F-\u2040"
      + NAME_START_CHARS;

  // basic tokens
  public static final String DOUBLE_QUOTE = "\"";
  public static final String SINGLE_QUOTE = "'";
  public static final String EQUALS = "=";
  public static final String WHITESPACE = " ";
  public static final String OPEN_COMMENT = "<!--";
  public static final String CLOSE_COMMENT = "-->";
  public static final String OPEN_CDATA = "<![CDATA[";
  public static final String CLOSE_CDATA = "]]>";
  public static final String OPEN_ELEMENT = "<";
  public static final String CLOSE_ELEMENT = ">";
  public static final String OPEN_END_ELEMENT = "</";
  public static final String CLOSE_END_ELEMENT = "/>";
  public static final String OPEN_DOCTYPE = "<!DOCTYPE";
  public static final String CLOSE_DOCTYPE = ">";
  public static final String OPEN_DOCTYPE_BLOCK = "[";
  public static final String CLOSE_DOCTYPE_BLOCK = "]";
  public static final String OPEN_PROCESSING = "<?";
  public static final String CLOSE_PROCESSING = "?>";

  // parser callbacks
  protected abstract TNode createAttribute(TName name, String text);
  protected abstract TNode createComment(String text);
  protected abstract TNode createCDATA(String text);
  protected abstract TNode createDoctype(String text);
  protected abstract TNode createDocument(Collection<TNode> children);
  protected abstract TNode createElement(TName name, Collection<TNode> attributes, Collection<TNode> children);
  protected abstract TNode createProcessing(String target, String text);
  protected abstract TName createQualified(String name);
  protected abstract TNode createText(String text);

  @Override
  @SuppressWarnings("unchecked")
  protected void initialize() {
    def("start", ref("document").end());

    def("attribute", ref("qualified")
        .seq(ref("space optional"))
        .seq(of(EQUALS))
        .seq(ref("space optional"))
        .seq(ref("attributeValue"))
        .map((List<?> list) -> createAttribute((TName) list.get(0), (String) list.get(4))));
    def("attributeValue", ref("attributeValueDouble")
        .or(ref("attributeValueSingle"))
        .pick(1));
    def("attributeValueDouble", of(DOUBLE_QUOTE)
        .seq(new XmlCharacterParser(DOUBLE_QUOTE, 0))
        .seq(of(DOUBLE_QUOTE)));
    def("attributeValueSingle", of(SINGLE_QUOTE)
        .seq(new XmlCharacterParser(SINGLE_QUOTE, 0))
        .seq(of(SINGLE_QUOTE)));
    def("attributes", ref("space")
        .seq(ref("attribute"))
        .pick(1)
        .star());
    def("comment", of(OPEN_COMMENT)
        .seq(any().starLazy(of(CLOSE_COMMENT)).flatten())
        .seq(of(CLOSE_COMMENT))
        .map((List<String> list) -> createComment(list.get(1))));
    def("cdata", of(OPEN_CDATA)
        .seq(any().starLazy(of(CLOSE_CDATA)).flatten())
        .seq(of(CLOSE_CDATA))
        .map((List<String> list) -> createCDATA(list.get(1))));
    def("content", ref("characterData")
        .or(ref("element"))
        .or(ref("processing"))
        .or(ref("comment"))
        .or(ref("cdata"))
        .star());
    def("doctype", of(OPEN_DOCTYPE)
        .seq(ref("space"))
        .seq(ref("nameToken")
            .or(ref("attributeValue"))
            .or(any()
                .starLazy(of(OPEN_DOCTYPE_BLOCK))
                .seq(of(OPEN_DOCTYPE_BLOCK))
                .seq(any().starLazy(of(CLOSE_DOCTYPE_BLOCK)))
                .seq(of(CLOSE_DOCTYPE_BLOCK)))
            .separatedBy(ref("space"))
            .flatten())
        .seq(ref("space optional"))
        .seq(of(CLOSE_DOCTYPE))
        .map((List<String> list) -> createDoctype(list.get(2))));
    def("document", ref("processing").optional()
        .seq(ref("misc"))
        .seq(ref("doctype").optional())
        .seq(ref("misc"))
        .seq(ref("element"))
        .seq(ref("misc"))
        .map((List<TNode> list) -> createDocument(Stream.of(list.get(0), list.get(2), list.get(4))
            .filter(Objects::nonNull)
            .collect(Collectors.toList()))));
    def("element", of(OPEN_ELEMENT)
        .seq(ref("qualified"))
        .seq(ref("attributes"))
        .seq(ref("space optional"))
        .seq(of(CLOSE_END_ELEMENT).or(of(CLOSE_ELEMENT)
            .seq(ref("content"))
            .seq(of(OPEN_END_ELEMENT))
            .seq(ref("qualified"))
            .seq(ref("space optional"))
            .seq(of(CLOSE_ELEMENT))))
        .map(new Function<List<?>, TNode>() {
          @Override
          public TNode apply(List<?> list) {
            if (list.get(4).equals(CLOSE_END_ELEMENT)) {
              return createElement((TName) list.get(1), (List<TNode>) list.get(2), Collections.emptyList());
            } else {
              List<?> end = (List<?>) list.get(4);
              if (list.get(1).equals(end.get(3))) {
                return createElement((TName) list.get(1), (List<TNode>) list.get(2), (List<TNode>) end.get(1));
              } else {
                throw new IllegalStateException("Expected </" + list.get(1) + ">, but found </" + end.get(3));
              }
            }
          }
        }));
    def("processing", of(OPEN_PROCESSING)
        .seq(ref("nameToken"))
        .seq(ref("space")
            .seq(any().starLazy(of(CLOSE_PROCESSING)).flatten())
            .pick(1).optional(""))
        .seq(of(CLOSE_PROCESSING))
        .map((List<String> list) -> createProcessing(list.get(1), list.get(2))));
    def("qualified", ref("nameToken")
        .map(this::createQualified));

    def("characterData", new XmlCharacterParser(OPEN_ELEMENT, 1).map(this::createText));
    def("misc", ref("space")
        .or(ref("comment"))
        .or(ref("processing"))
        .star());
    def("space", whitespace().plus());
    def("space optional", whitespace().star());

    def("nameToken", ref("nameStartChar")
        .seq(ref("nameChar").star())
        .flatten());
    def("nameStartChar", pattern(NAME_START_CHARS, "Expected name"));
    def("nameChar", pattern(NAME_CHARS));
  }
}
