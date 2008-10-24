package com.intellij.application.options.colors;

import com.intellij.application.options.colors.highlighting.HighlightData;
import com.intellij.application.options.colors.highlighting.HighlightsExtractor;
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.EditorHighlightingProvidingColorSettingsPage;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;

import java.awt.*;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public class SimpleEditorPreview implements PreviewPanel{
  private final ColorSettingsPage myPage;

  private final EditorEx myEditor;
  private final Alarm myBlinkingAlarm;
  private final HighlightData[] myHighlightData;

  private final ColorAndFontOptions myOptions;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public SimpleEditorPreview(final ColorAndFontOptions options, final ColorSettingsPage page) {
    myOptions = options;
    myPage = page;

    String text = page.getDemoText();

    HighlightsExtractor extractant2 = new HighlightsExtractor(page.getAdditionalHighlightingTagToDescriptorMap());
    myHighlightData = extractant2.extractHighlights(text);

    myEditor = (EditorEx)createEditor(extractant2.cutDefinedTags(text), 10, 3, -1);

    ErrorStripeRenderer renderer = new TrafficLightRenderer(null,null,null,null){
      protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus() {
        DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
        status.errorAnalyzingFinished = true;
        status.passStati = new ArrayList<DaemonCodeAnalyzerStatus.PassStatus>();
        status.errorCount = new int[]{1, 2};
        return status;
      }
    };
    ((EditorMarkupModel)myEditor.getMarkupModel()).setErrorStripeRenderer(renderer);
    ((EditorMarkupModel)myEditor.getMarkupModel()).setErrorStripeVisible(true);
    myBlinkingAlarm = new Alarm().setActivationComponent(myEditor.getComponent());

    //TODO
    /*
    new ClickNavigator(tab.myOptionsList).addClickNavigator(editor,
                                                            page.getHighlighter(),
                                                            highlightData,
                                                            false
    );*/

    addMouseMotionListener(myEditor, page.getHighlighter(), myHighlightData, false);

    CaretListener listener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        navigate(myEditor, true, e.getNewPosition(), page.getHighlighter(), myHighlightData, false);
      }
    };
    myEditor.getCaretModel().addCaretListener(listener);


  }

  private void addMouseMotionListener(final Editor view,
                                      final SyntaxHighlighter highlighter,
                                      final HighlightData[] data, final boolean isBackgroundImportant) {
    view.getContentComponent().addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        LogicalPosition pos = view.xyToLogicalPosition(new Point(e.getX(), e.getY()));
        navigate(view, false, pos, highlighter, data, isBackgroundImportant);
      }
    });
  }

  private void navigate(final Editor editor, boolean select,
                        LogicalPosition pos,
                        final SyntaxHighlighter highlighter,
                        final HighlightData[] data, final boolean isBackgroundImportant) {
    int offset = editor.logicalPositionToOffset(pos);

    if (!isBackgroundImportant && editor.offsetToLogicalPosition(offset).column != pos.column) {
      if (!select) {
        ClickNavigator.setCursor(editor, Cursor.TEXT_CURSOR);
        return;
      }
    }

    if (data != null) {
      for (HighlightData highlightData : data) {
        if (ClickNavigator.highlightDataContainsOffset(highlightData, editor.logicalPositionToOffset(pos))) {
          if (!select) {
            ClickNavigator.setCursor(editor, Cursor.HAND_CURSOR);
          }
          else {
            myDispatcher.getMulticaster().selectionInPreviewChanged(highlightData.getHighlightType());
          }
          return;
        }
      }
    }

    if (highlighter != null) {
      HighlighterIterator itr = ((EditorEx)editor).getHighlighter().createIterator(offset);
      selectItem(itr, highlighter, select);
      if (!select) {
        ClickNavigator.setCursor(editor, Cursor.HAND_CURSOR);
      }
      else {
        ClickNavigator.setCursor(editor, Cursor.TEXT_CURSOR);
      }
    }
  }

  private void selectItem(HighlighterIterator itr, SyntaxHighlighter highlighter, final boolean select) {

    IElementType tokenType = itr.getTokenType();
    if (tokenType == null) return;
    String type = ClickNavigator.highlightingTypeFromTokenType(tokenType, highlighter);
    if (select) {
      myDispatcher.getMulticaster().selectionInPreviewChanged(type);
    }
  }

  private Editor createEditor(String text, int column, int line, int selectedLine) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument(text);
    EditorEx editor = (EditorEx)editorFactory.createViewer(editorDocument);
    editor.setColorsScheme(myOptions.getSelectedScheme());
    EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(true);
    settings.setWhitespacesShown(true);
    settings.setLineMarkerAreaShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setRightMarginShown(true);
    settings.setRightMargin(60);

    LogicalPosition pos = new LogicalPosition(line, column);
    editor.getCaretModel().moveToLogicalPosition(pos);
    if (selectedLine >= 0) {
      editor.getSelectionModel().setSelection(editorDocument.getLineStartOffset(selectedLine),
                                              editorDocument.getLineEndOffset(selectedLine));
    }

    return editor;
  }

  public Component getPanel() {
    return myEditor.getComponent();
  }

  public void updateView() {
    EditorHighlighter highlighter = null;
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    myEditor.setColorsScheme(scheme);

    if (myPage instanceof EditorHighlightingProvidingColorSettingsPage) {

      highlighter = ((EditorHighlightingProvidingColorSettingsPage)myPage).createEditorHighlighter(scheme);
    }
    if (highlighter == null) {
      final SyntaxHighlighter pageHighlighter = myPage.getHighlighter();
      highlighter = HighlighterFactory.createHighlighter(pageHighlighter, scheme);
    }
    myEditor.setHighlighter(highlighter);
    updateHighlighters();

    myEditor.reinitSettings();

  }

  private void updateHighlighters() {
    HighlightData[] datum = myHighlightData;
    final Map<TextAttributesKey, String> displayText = ColorSettingsUtil.keyToDisplayTextMap(myPage);
    for (final HighlightData data : datum) {
      data.addHighlToView(myEditor, myOptions.getSelectedScheme(), displayText);
    }
  }

  private static final int BLINK_COUNT = 3 * 2;

  public void blinkSelectedHighlightType(EditorSchemeAttributeDescriptor description) {
    if (description == null) return;
    String type = description.getType();

    java.util.List<HighlightData> highlights = startBlinkingHighlights(myEditor,
                                                                       myHighlightData, type,
                                                             myPage.getHighlighter(), true,
                                                             myBlinkingAlarm, BLINK_COUNT, myPage);

    scrollHighlightInView(highlights, type, myEditor);
  }

  private static void scrollHighlightInView(final java.util.List<HighlightData> highlightDatas, final String type, final Editor editor) {
    boolean needScroll = true;
    int minOffset = Integer.MAX_VALUE;
    for(HighlightData data: highlightDatas) {
      if (isOffsetVisible(editor, data.getStartOffset())) {
        needScroll = false;
        break;
      }
      minOffset = Math.min(minOffset, data.getStartOffset());
    }
    if (needScroll && minOffset != Integer.MAX_VALUE) {
      LogicalPosition pos = editor.offsetToLogicalPosition(minOffset);
      editor.getScrollingModel().scrollTo(pos, ScrollType.MAKE_VISIBLE);
    }
  }

  private static boolean isOffsetVisible(final Editor editor, final int startOffset) {
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    Point point = editor.logicalPositionToXY(editor.offsetToLogicalPosition(startOffset));
    return point.y >= visibleArea.y && point.y < visibleArea.x + visibleArea.height;
  }

  private void stopBlinking() {
    myBlinkingAlarm.cancelAllRequests();
  }

  private java.util.List<HighlightData> startBlinkingHighlights(final EditorEx editor,
                                                      final HighlightData[] highlightDatum,
                                                      final String attrKey,
                                                      final SyntaxHighlighter highlighter,
                                                      final boolean show,
                                                      final Alarm alarm,
                                                      final int count,
                                                      final ColorSettingsPage page) {
    if (show && count <= 0) return Collections.emptyList();
    editor.getMarkupModel().removeAllHighlighters();
    boolean found = false;
    java.util.List<HighlightData> highlights = new ArrayList<HighlightData>();
    java.util.List<HighlightData> matchingHighlights = new ArrayList<HighlightData>();
    for (int i = 0; highlightDatum != null && i < highlightDatum.length; i++) {
      HighlightData highlightData = highlightDatum[i];
      String type = highlightData.getHighlightType();
      highlights.add(highlightData);
      if (show && type.equals(attrKey)) {
        highlightData =
        new HighlightData(highlightData.getStartOffset(), highlightData.getEndOffset(),
                          CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
        highlights.add(highlightData);
        matchingHighlights.add(highlightData);
        found = true;
      }
    }
    if (!found && highlighter != null) {
      HighlighterIterator iterator = editor.getHighlighter().createIterator(0);
      do {
        IElementType tokenType = iterator.getTokenType();
        TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);
        for (final TextAttributesKey tokenHighlight : tokenHighlights) {
          String type = tokenHighlight.getExternalName();
          if (show && type != null && type.equals(attrKey)) {
            HighlightData highlightData = new HighlightData(iterator.getStart(), iterator.getEnd(),
                                                            CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
            highlights.add(highlightData);
            matchingHighlights.add(highlightData);
          }
        }
        iterator.advance();
      }
      while (!iterator.atEnd());
    }

    final Map<TextAttributesKey, String> displayText = ColorSettingsUtil.keyToDisplayTextMap(page);

    // sort highlights to avoid overlappings
    Collections.sort(highlights, new Comparator<HighlightData>() {
      public int compare(HighlightData highlightData1, HighlightData highlightData2) {
        return highlightData1.getStartOffset() - highlightData2.getStartOffset();
      }
    });
    for (int i = highlights.size() - 1; i >= 0; i--) {
      HighlightData highlightData = highlights.get(i);
      int startOffset = highlightData.getStartOffset();
      HighlightData prevHighlightData = i == 0 ? null : highlights.get(i - 1);
      if (prevHighlightData != null
          && startOffset <= prevHighlightData.getEndOffset()
          && highlightData.getHighlightType().equals(prevHighlightData.getHighlightType())) {
        prevHighlightData.setEndOffset(highlightData.getEndOffset());
      }
      else {
        highlightData.addHighlToView(editor, myOptions.getSelectedScheme(), displayText);
      }
    }
    alarm.cancelAllRequests();
    alarm.addComponentRequest(new Runnable() {
      public void run() {
        startBlinkingHighlights(editor, highlightDatum, attrKey, highlighter, !show, alarm, count - 1, page);
      }
    }, 400);
    return matchingHighlights;
  }


  public void addListener(final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }
}
