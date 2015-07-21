package translator.logic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.xerces.dom.DocumentImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A translator factory that translates Compumedics annotation file and generate
 * specified output
 * 
 * @author wei wang, 2014-8-21
 */
public class ARESTranslatorFactory extends AbstractTranslatorFactory {

  private Document xmlRoot; // = new DocumentImpl(); // xml root
  private Element scoredEvents; // parent element of <Event>

  public ARESTranslatorFactory() {
    super();
    System.out
        .println("=================================================================");
    System.out.println("Compumedics Factory:"); // test
    softwareVersion = "Compumedics";
    xmlRoot = new DocumentImpl(); // xml root
  }

  @Override
  public boolean read(String edfFile, String annotationFile, String mappingFile) {
    boolean result = false;
    this.edfFile = edfFile;
    this.xmlAnnotation = annotationFile;
    map = readMapFile(mappingFile);
    initLocalVariables(edfFile); // initialize local variables
    document = resolveBOM(xmlAnnotation);
    result = recordEvents(document);
    if (!result) {
      log("Cannot parse the events in the annotation file");
    }
    return result;
  }

  @Override
  public boolean translate() {
    boolean result = false;
    Element root = createEmptyDocument(softwareVersion);

    NodeList nodeList = document.getElementsByTagName("ScoredEvent");
    for (int index = 0; index < nodeList.getLength(); index++) {
      Element parsedElement = null;
      Node node = nodeList.item(index); // for each <event> node
      Element elem = (Element) node;
      parsedElement = parseCompumedicsXmlEvent(elem);
      if (parsedElement == null) {
        log("Can't parse event: " + index + " "
            + getElementByChildTag(elem, "Type"));
      }
      scoredEvents.appendChild(parsedElement);
    }
    // Parse staging:
    for (Element elem : parseStaging())
      scoredEvents.appendChild(elem);

    root.appendChild(scoredEvents);
    xmlRoot.appendChild(root);
    result = true;
    return result;
  }

  /**
   * Parses staging element and appends them to scoredEvents element
   * 
   * @return a list containing parsed staging event
   */
  private List<Element> parseStaging() {
    List<Element> list = new ArrayList<Element>();
    // for each sleep stages
    NodeList stageList = document.getElementsByTagName("SleepStage");
    Element eventCategory = xmlRoot.createElement("EventType");
    Element scoredEvent = xmlRoot.createElement("ScoredEvent");
    Element eventConcept = xmlRoot.createElement("EventConcept");
    Element startElement = xmlRoot.createElement("Start");
    Element durationElement = xmlRoot.createElement("Duration");

    String firstStageKey = ((Element) stageList.item(0)).getTextContent();

    String firstStageValue;
    String firstStageCategory;
    if (map[2].keySet().contains(firstStageKey)) {
      firstStageValue = (String) map[2].get(firstStageKey);
      firstStageCategory = (String) map[3].get(firstStageKey);
    } else {
      firstStageValue = "";
      firstStageCategory = "";
    }
    double start = 0;
    eventCategory.appendChild(xmlRoot.createTextNode(firstStageCategory));
    eventConcept.appendChild(xmlRoot.createTextNode(firstStageValue));
    startElement.appendChild(xmlRoot.createTextNode(Double.toString(start)));
    scoredEvent.appendChild(eventCategory);
    scoredEvent.appendChild(eventConcept);
    scoredEvent.appendChild(startElement);
    int count = 1; // original: count=0;
    for (int i = 1; i < stageList.getLength(); i++) {
      String iStageValue = ((Element) stageList.item(i)).getTextContent();
      if (iStageValue.compareTo(firstStageKey) == 0) {
        count++; 
      } else {
        // process last repeating stage event
        durationElement.appendChild(xmlRoot.createTextNode(Double
            .toString(count * 30)));
        scoredEvent.appendChild(durationElement);
        list.add(scoredEvent);
        scoredEvent = xmlRoot.createElement("ScoredEvent");
        eventConcept = xmlRoot.createElement("EventConcept");
        eventCategory = xmlRoot.createElement("EventType");
        firstStageKey = iStageValue;
        if (map[2].keySet().contains(firstStageKey)) {
          firstStageValue = (String) map[2].get(firstStageKey);
          firstStageCategory = (String) map[3].get(firstStageKey);
        } else {
          firstStageValue = "";
          firstStageCategory = "";
        }
        eventCategory.appendChild(xmlRoot.createTextNode(firstStageCategory));
        eventConcept.appendChild(xmlRoot.createTextNode(firstStageValue));
        startElement = xmlRoot.createElement("Start");
        start += count * 30; // start = start + count * 30;
        startElement
            .appendChild(xmlRoot.createTextNode(Double.toString(start)));
        durationElement = xmlRoot.createElement("Duration");
        scoredEvent.appendChild(eventCategory);
        scoredEvent.appendChild(eventConcept);
        scoredEvent.appendChild(startElement);
        count = 1;
      }
    }
    durationElement.appendChild(xmlRoot.createTextNode(Double
        .toString(count * 30)));
    scoredEvent.appendChild(durationElement);
    list.add(scoredEvent);
    return list;
  }

  private Element parseCompumedicsXmlEvent(Element scoredEventElement) {
    // only DESAT type has more values to be processed, others are the same
    Element scoredEvent = null;
    String eventType = getElementByChildTag(scoredEventElement, "Name");
    // map[1] contains keySet with event name
    if (map[1].keySet().contains(eventType)) {
      scoredEvent = parseEventElement(scoredEventElement);
    } else {
      // no mapping event name found
      scoredEvent = xmlRoot.createElement("ScoredEvent");
      Element eventConcept = xmlRoot.createElement("EventConcept");
      Element eventCategory = xmlRoot.createElement("EventType");
      Element startElement = xmlRoot.createElement("Start");
      Element durationElement = xmlRoot.createElement("Duration");
      Element notesElement = xmlRoot.createElement("Notes");

      eventConcept.appendChild(xmlRoot.createTextNode("Technician Notes"));
      notesElement.appendChild(xmlRoot.createTextNode(eventType));
      eventCategory.appendChild(xmlRoot.createTextNode("Technician Notes"));

      String startTime = getElementByChildTag(scoredEventElement, "Start");
      startElement.appendChild(xmlRoot.createTextNode(startTime));
      String durationTime = getDuration(scoredEventElement);
      durationElement.appendChild(xmlRoot.createTextNode(durationTime));

      scoredEvent.appendChild(eventCategory);
      scoredEvent.appendChild(eventConcept);
      scoredEvent.appendChild(startElement);
      scoredEvent.appendChild(durationElement);
      scoredEvent.appendChild(notesElement);
      String info = xmlAnnotation + "," + eventType + "," + startTime;
      log(info);
    }

    return scoredEvent;
  }

  private Element parseEventElement(Element scoredEventElement) {
    List<Element> locationList = getLocation(scoredEventElement);
    if (locationList == null) {
      log("ERROR: location error");
    }
    List<Element> userVariableList = getUserVariables(scoredEventElement);
    if (userVariableList == null) {
      log("ERROR: user variable error");
    }
    Element scoredEvent = null;
    if (xmlRoot != null) {
      scoredEvent = xmlRoot.createElementNS(null, "ScoredEvent");
    } else {
      log("ERROR: root element is null");
      return null;
    }
    for (Element element : locationList)
      scoredEvent.appendChild(element);
    for (Element element : userVariableList)
      scoredEvent.appendChild(element);
    return scoredEvent;
  }

  private List<Element> getUserVariables(Element scoredEventElement) {
    List<Element> list = new ArrayList<Element>();
    String eventType = getElementByChildTag(scoredEventElement, "Name");
    if (eventType.equals("SpO2 desaturation")) {
      Element spO2Nadir = xmlRoot.createElement("SpO2Nadir");
      Element spO2Baseline = xmlRoot.createElement("SpO2Baseline");
      String desatStartVal = getElementByChildTag(scoredEventElement,
          "LowestSpO2");
      String desat = getElementByChildTag(scoredEventElement, "Desaturation");
      String desatEndVal = String.valueOf(Double.parseDouble(desatStartVal)
          + Double.parseDouble(desat));
      spO2Nadir.appendChild(xmlRoot.createTextNode(desatStartVal));
      spO2Baseline.appendChild(xmlRoot.createTextNode(desatEndVal));
      list.add(spO2Nadir);
      list.add(spO2Baseline);
    }
    return list;
  }

  private List<Element> getLocation(Element scoredEventElement) {
    // {eventConcept, duration, start}
    List<Element> list = new ArrayList<Element>();
    String eventType = getElementByChildTag(scoredEventElement, "Name");
    String annLocation = getElementByChildTag(scoredEventElement, "Input");
    Element eventCategory = xmlRoot.createElement("EventType");
    Element eventConcept = xmlRoot.createElement("EventConcept");
    Element duration = xmlRoot.createElement("Duration");
    Element start = xmlRoot.createElement("Start");
    Element input = xmlRoot.createElement("SignalLocation");
    @SuppressWarnings("unchecked")
    Node nameNode = xmlRoot.createTextNode((String) ((ArrayList<String>) map[1]
        .get(eventType)).get(1));
    String categoryStr = map[3].get(eventType) == null ? "" : (String) map[3]
        .get(eventType);
    Node categoryNode = xmlRoot.createTextNode(categoryStr);

    String signalLocation = getSignalLocationFromEvent(scoredEventElement,
        annLocation);
    Node inputNode = xmlRoot.createTextNode(signalLocation);
    eventCategory.appendChild(categoryNode);
    eventConcept.appendChild(nameNode);
    input.appendChild(inputNode);

    String startTime = getElementByChildTag(scoredEventElement, "Start");
    String durationTime = getElementByChildTag(scoredEventElement, "Duration");
    duration.appendChild(xmlRoot.createTextNode(durationTime));
    start.appendChild(xmlRoot.createTextNode(startTime));

    list.add(eventCategory);
    list.add(eventConcept);
    list.add(start);
    list.add(duration);
    list.add(input);

    return list;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * translator.logic.AbstractTranslatorFactory#getSignalLocationFromEvent(org
   * .w3c.dom.Element, java.lang.String)
   */
  public String getSignalLocationFromEvent(Element scoredEvent,
      String annLocation) {

    String result = signalLabels[0]; // initialize to the first EDF signal

    String eventName = getElementByChildTag(scoredEvent, "Name");
    @SuppressWarnings("unchecked")
    List<String> defaultSignals = (List<String>) map[4].get(eventName);
    List<String> edfSignals = Arrays.asList(signalLabels);

    if (edfSignals.contains(annLocation)) {
      result = annLocation;
    } else {
      for (String signal : defaultSignals) {
        if (edfSignals.contains(signal)) {
          result = signal;
        }
      }
    }

    return result;
  }

  private String getDuration(Element scoredEvent) {
    String duration;
    duration = getElementByChildTag(scoredEvent, "Duration");
    if (duration != null) {
      return duration;
    } else {
      log("Duration not found: " + getElementByChildTag(scoredEvent, "Name"));
      return "";
    }
  }

  @Override
  public boolean write2xml(String outputFile) {
    System.out.println("   >>> Inside CompumedicsAnnotationTranslator write"); // test
    output = outputFile;
    try {
      Transformer transformer = TransformerFactory.newInstance()
          .newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      DOMSource source = new DOMSource(xmlRoot);
      StreamResult file = new StreamResult(new File(output));
      transformer.transform(source, file);
      log("XML DOM Created Successfully..");
      System.out.println("   [Write done]");
      System.out
          .println("=================================================================");
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private Element createEmptyDocument(String softwareVersion) {
    Element root = xmlRoot.createElement("PSGAnnotation");
    Element software = xmlRoot.createElement("SoftwareVersion");
    software.appendChild(xmlRoot.createTextNode(softwareVersion));
    Element epoch = xmlRoot.createElement("EpochLength");
    String epochLength = (String) map[0].get("EpochLength");
    if (epochLength != null) {
      epoch.appendChild(xmlRoot.createTextNode(epochLength));
    } else {
      epoch.appendChild(xmlRoot.createTextNode(""));
    }
    root.appendChild(software);
    root.appendChild(epoch);

    scoredEvents = xmlRoot.createElement("ScoredEvents");
    String[] elmts = new String[3];//
    elmts[0] = "Recording Start Time";//
    elmts[1] = "0";//
    elmts[2] = timeStart[1];//
    Element timeElement = addElements(xmlRoot, elmts);
    Element clock = xmlRoot.createElement("ClockTime");
    clock.appendChild(xmlRoot.createTextNode(timeStart[0]));
    timeElement.appendChild(clock);
    scoredEvents.appendChild(timeElement);
    return root;
  }

  /**
   * Parses the Embla xml annotation file and generates the event names
   * 
   * @param emblaXmlFile
   *          the Embla annotation file
   * @return true if the process is successful
   */
  private boolean recordEvents(Document doc) {
    String eventName;
    Set<String> eventNames = new HashSet<String>();
    NodeList nodeList = doc.getElementsByTagName("ScoredEvent");
    if (nodeList == null) {
      log("Cannot find EventType");
      return false;
    }
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node node = nodeList.item(i);
      if (node.hasChildNodes()) {
        Node last = node.getLastChild();
        eventName = last.getNodeValue();
        eventNames.add(eventName);
      }
    }
    events = eventNames;
    return true;
  }

  /**
   * Appends elements of string format to the ScoredEvent element
   * 
   * @param doc
   *          the Document to which the elements to be added
   * @param elements
   *          elements to be added to the ScoredEvent element
   * @return the ScoredEvent element
   */
  private Element addElements(Document doc, String[] elements) {
    Element eventElement = doc.createElement("ScoredEvent");
    Element nameElement = doc.createElement("EventConcept");
    Element typeElement = doc.createElement("EventType");
    typeElement.appendChild(doc.createTextNode(""));
    nameElement.appendChild(doc.createTextNode(elements[0]));
    eventElement.appendChild(typeElement);
    eventElement.appendChild(nameElement);
    Element startElement = doc.createElement("Start");
    startElement.appendChild(doc.createTextNode(elements[1]));
    eventElement.appendChild(startElement);
    Element durationElement = doc.createElement("Duration");
    durationElement.appendChild(doc.createTextNode(elements[2]));
    eventElement.appendChild(durationElement);
    return eventElement;
  }

  @Override
  public boolean write2JSON(String outputFile) {
    boolean result = false;
    String resultFinal = FormatedWriter.xml2json(xmlRoot);
    try (PrintStream out = new PrintStream(new FileOutputStream(outputFile))) {
      out.print(resultFinal);
      result = true;
    } catch (Exception e) {
      // log result
    }
    return result;
  }

  /**
   * Reads mapping file and saves as an array of HashMap Can be put in a higher
   * hierarchy
   * 
   * @param mapFile
   *          the mapping file name
   * @return the mapping in form of HashMap
   */
  public HashMap<String, Object>[] readMapFile(String mapFile) {
    @SuppressWarnings("unchecked")
    HashMap<String, Object>[] map = (HashMap<String, Object>[]) Array
        .newInstance(HashMap.class, 5);
    try {
      BufferedReader input = new BufferedReader(new FileReader(mapFile));
      try {
        String line = input.readLine();
        HashMap<String, Object> epoch = new HashMap<String, Object>();
        HashMap<String, Object> events = new HashMap<String, Object>();
        HashMap<String, Object> stages = new HashMap<String, Object>();
        HashMap<String, Object> categories = new HashMap<String, Object>();
        HashMap<String, Object> signalLocation = new HashMap<String, Object>(); // /

        while ((line = input.readLine()) != null) {
          String[] data = line.split(",");
          String eventTypeLowerCase = data[0].toLowerCase();
          String eventCategoryInPipe = data[0] + "|" + data[0];
          String eventNameInPipe = data[1].trim() + "|" + data[3].trim();
          List<String> defaultSignals = new ArrayList<>();
          // process events
          if (!eventTypeLowerCase.contains("epochlength")
              && !eventTypeLowerCase.contains("stages")) {

            // Process signal column in mapping file
            if (data[4].length() != 0) {
              for (String sname : data[4].split("#")) {
                defaultSignals.add(sname);
              }
            }

            // values: {EventType, EventConcept, Note}
            ArrayList<String> values = new ArrayList<String>(3);
            values.add(data[0]);
            values.add(eventNameInPipe);
            if (data.length > 5) {
              values.add(data[5]); // add Notes
            }
            // events {event, event_type && event_concept}
            events.put(data[3].trim(), values);
            categories.put(data[3].trim(), eventCategoryInPipe);
            signalLocation.put(data[3].trim(), defaultSignals);
          }
          // Dated process for epoch
          else if (data[0].compareTo("EpochLength") == 0) {
            // System.out.println(data[0]);
            epoch.put(data[0], data[2]);
          } else {
            // stages {event, event_concept}
            stages.put(data[3].trim(), eventNameInPipe);
            categories.put(data[3].trim(), eventCategoryInPipe);
          }
        }
        // System.out.println(map[2].values().size());
        map[0] = epoch;
        map[1] = events;
        map[2] = stages;
        map[3] = categories;
        map[4] = signalLocation;
      } finally {
        input.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
      StringWriter errors = new StringWriter();
      e.printStackTrace(new PrintWriter(errors));
      log(errors.toString());
    }
    return map;
  }
}

class ItemARES {
  private int code;
  private int attribute;
  private int subattribute;
  private String category;
  private String cddName;
  private String aresName;
  
  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    String result = String.valueOf(code);
    result += String.valueOf(attribute);
    result += String.valueOf(subattribute);
    return Integer.valueOf(result);
  }
  
  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override 
  public boolean equals(Object obj) {
    try {
      ItemARES anotherItem = (ItemARES) obj;
      if (anotherItem.hashCode() == this.hashCode()) {
        return true;
      } else {
        return false;
      }
    } catch (Exception e) {
      return false;
    }
  }
  
  public void setCode(int code) {
    this.code = code;
  }
  
  public int getCode() {
    return code;
  }
  
  public void setAttribute(int attribute) {
    this.attribute = attribute;
  }
  
  public int getAttribute() {
    return attribute;
  }
  
  public void setSubattribute(int subattribute) {
    this.subattribute = subattribute;
  }
  
  public int getSubattribute() {
    return subattribute;
  }
  
  public void setCategory(String category) {
    this.category = category;
  }
  
  public String getCategory() {
    return category;
  }

  public void setCddName(String cddName) {
    this.cddName = cddName;
  }
  
  public String getCddName() {
    return cddName;
  }

  public void setAresName(String aresName) {
    this.aresName = aresName;
  }
  
  public String getAresName() {
    return aresName;
  }
}