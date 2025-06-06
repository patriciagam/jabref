package org.jabref.gui.maintable.columns;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import org.jabref.gui.DialogService;
import org.jabref.gui.externalfiletype.ExternalFileType;
import org.jabref.gui.externalfiletype.ExternalFileTypes;
import org.jabref.gui.fieldeditors.LinkedFileViewModel;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.icon.JabRefIcon;
import org.jabref.gui.maintable.BibEntryTableViewModel;
import org.jabref.gui.maintable.ColumnPreferences;
import org.jabref.gui.maintable.MainTableColumnFactory;
import org.jabref.gui.maintable.MainTableColumnModel;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.util.ValueTableCellFactory;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.TaskExecutor;
import org.jabref.logic.util.io.FileUtil;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.LinkedFile;

/**
 * A column that draws a clickable symbol for either all the files of a defined file type
 * or a joined column with all the files of any type
 */
public class FileColumn extends MainTableColumn<List<LinkedFile>> {

    private final DialogService dialogService;
    private final BibDatabaseContext database;
    private final GuiPreferences preferences;
    private final TaskExecutor taskExecutor;

    /**
     * Creates a joined column for all the linked files.
     */
    public FileColumn(MainTableColumnModel model,
                      BibDatabaseContext database,
                      DialogService dialogService,
                      GuiPreferences preferences,
                      TaskExecutor taskExecutor) {
        super(model);
        this.database = Objects.requireNonNull(database);
        this.dialogService = dialogService;
        this.preferences = preferences;
        this.taskExecutor = taskExecutor;

        setCommonSettings();

        Node headerGraphic = IconTheme.JabRefIcons.FILE.getGraphicNode();
        Tooltip.install(headerGraphic, new Tooltip(Localization.lang("Linked files")));
        this.setGraphic(headerGraphic);

        new ValueTableCellFactory<BibEntryTableViewModel, List<LinkedFile>>()
                .withGraphic(this::createFileIcon)
                .withTooltip(this::createFileTooltip)
                .withMenu(this::createFileMenu)
                .withOnMouseClickedEvent((entry, linkedFiles) -> event -> {
                    if ((event.getButton() == MouseButton.PRIMARY) && (linkedFiles.size() == 1)) {
                        // Only one linked file -> open directly
                        LinkedFileViewModel linkedFileViewModel = new LinkedFileViewModel(linkedFiles.getFirst(),
                                entry.getEntry(),
                                database,
                                taskExecutor,
                                dialogService,
                                preferences);
                        linkedFileViewModel.open();
                    }
                })
                .install(this);
    }

    /**
     * Creates a column for all the linked files of a single file type.
     */
    public FileColumn(MainTableColumnModel model,
                      BibDatabaseContext database,
                      DialogService dialogService,
                      GuiPreferences preferences,
                      String fileType,
                      TaskExecutor taskExecutor) {
        super(model);
        this.database = Objects.requireNonNull(database);
        this.dialogService = dialogService;
        this.preferences = preferences;
        this.taskExecutor = taskExecutor;

        setCommonSettings();

        this.setGraphic(ExternalFileTypes.getExternalFileTypeByName(fileType, preferences.getExternalApplicationsPreferences())
                                         .map(ExternalFileType::getIcon).orElse(IconTheme.JabRefIcons.FILE)
                                         .getGraphicNode());

        new ValueTableCellFactory<BibEntryTableViewModel, List<LinkedFile>>()
                .withGraphic((entry, linkedFiles) -> createFileIcon(entry, linkedFiles.stream()
                                                                                      .filter(linkedFile -> linkedFile.getFileType().equalsIgnoreCase(fileType))
                                                                                      .collect(Collectors.toList())))
                .withOnMouseClickedEvent((entry, linkedFiles) -> event -> {
                    List<LinkedFile> filteredFiles = linkedFiles.stream()
                                                                .filter(linkedFile -> linkedFile.getFileType().equalsIgnoreCase(fileType))
                                                                .collect(Collectors.toList());

                    if (event.getButton() == MouseButton.PRIMARY) {
                        if (filteredFiles.size() == 1) {
                            // Only one file - open directly
                            LinkedFileViewModel linkedFileViewModel = new LinkedFileViewModel(filteredFiles.getFirst(),
                                    entry.getEntry(), database, taskExecutor, dialogService, preferences);
                            linkedFileViewModel.open();
                        } else if (filteredFiles.size() > 1) {
                            // Multiple files - show context menu to choose file
                            ContextMenu contextMenu = new ContextMenu();
                            for (LinkedFile linkedFile : filteredFiles) {
                                LinkedFileViewModel linkedFileViewModel = new LinkedFileViewModel(linkedFile,
                                        entry.getEntry(), database, taskExecutor, dialogService, preferences);
                                MenuItem menuItem = new MenuItem(linkedFileViewModel.getTruncatedDescriptionAndLink(),
                                        linkedFileViewModel.getTypeIcon().getGraphicNode());
                                menuItem.setOnAction(e -> linkedFileViewModel.open());
                                contextMenu.getItems().add(menuItem);
                            }
                            contextMenu.show((Node) event.getSource(), event.getScreenX(), event.getScreenY());
                        }
                    }
                })
                .install(this);
    }

    private void setCommonSettings() {
        this.setResizable(false);
        MainTableColumnFactory.setExactWidth(this, ColumnPreferences.ICON_COLUMN_WIDTH);
        this.getStyleClass().add(MainTableColumnFactory.STYLE_ICON_COLUMN);
        this.setCellValueFactory(cellData -> cellData.getValue().getLinkedFiles());
    }

    private String createFileTooltip(List<LinkedFile> linkedFiles) {
        if (linkedFiles.isEmpty()) {
            return null;
        }

        String filePaths = linkedFiles.stream()
                                      .map(LinkedFile::getLink)
                                      .collect(Collectors.joining("\n"));

        if (linkedFiles.size() == 1) {
            return Localization.lang("Open file %0", filePaths);
        }
        return filePaths;
    }

    private ContextMenu createFileMenu(BibEntryTableViewModel entry, List<LinkedFile> linkedFiles) {
        if (linkedFiles.size() <= 1) {
            return null;
        }

        ContextMenu contextMenu = new ContextMenu();

        for (LinkedFile linkedFile : linkedFiles) {
            LinkedFileViewModel linkedFileViewModel = new LinkedFileViewModel(linkedFile,
                    entry.getEntry(),
                    database,
                    taskExecutor,
                    dialogService,
                    preferences);

            MenuItem menuItem = new MenuItem(linkedFileViewModel.getTruncatedDescriptionAndLink(),
                    linkedFileViewModel.getTypeIcon().getGraphicNode());
            menuItem.setOnAction(event -> linkedFileViewModel.open());
            contextMenu.getItems().add(menuItem);
        }

        return contextMenu;
    }

    public Node createFileIcon(BibEntryTableViewModel entry, List<LinkedFile> linkedFiles) {
        if (entry.hasFullTextResultsProperty().get()) {
            return IconTheme.JabRefIcons.FILE_SEARCH.getGraphicNode();
        }

        String user = System.getProperty("user.name");
        HBox icons = new HBox(2);
        icons.setAlignment(Pos.CENTER);

        for (LinkedFile plain : linkedFiles) {
            if (plain.isCommented()) {
                continue;
            }

            boolean plainExists = FileUtil.find(
                    database, plain.getLink(), preferences.getFilePreferences()).isPresent();

            JabRefIcon pdfIcon = ExternalFileTypes
                    .getExternalFileTypeByLinkedFile(
                            plain, true, preferences.getExternalApplicationsPreferences())
                    .map(ExternalFileType::getIcon)
                    .orElse(IconTheme.JabRefIcons.FILE);

            Node baseIcon = (plainExists ? pdfIcon : pdfIcon.disabled()).getGraphicNode();
            baseIcon.setOnMouseClicked(ev -> {
                new LinkedFileViewModel(plain, entry.getEntry(), database,
                        taskExecutor, dialogService, preferences).open();
                ev.consume();
            });
            icons.getChildren().add(baseIcon);

            boolean ownsComment = linkedFiles.stream().anyMatch(other ->
                    other.isCommented()
                            && sameBase(other, plain)
                            && other.getLink().contains(" - comments " + user + ".pdf"));

            if (ownsComment) {
                icons.getChildren().add(createBubbleNode(entry, linkedFiles, plain, user));
            }
        }

        if (icons.getChildren().isEmpty() && !linkedFiles.isEmpty()) {
            LinkedFile comment = linkedFiles.getFirst();
            JabRefIcon pdfIcon = ExternalFileTypes
                    .getExternalFileTypeByLinkedFile(
                            comment, true,
                            preferences.getExternalApplicationsPreferences())
                    .map(ExternalFileType::getIcon)
                    .orElse(IconTheme.JabRefIcons.FILE);

            Node disabledPdf = pdfIcon.disabled().getGraphicNode();
            disabledPdf.setOnMouseClicked(ev -> {
                new LinkedFileViewModel(comment, entry.getEntry(), database,
                        taskExecutor, dialogService, preferences).open();
                ev.consume();
            });
            icons.getChildren().add(disabledPdf);
            icons.getChildren().add(createBubbleNode(entry, linkedFiles, comment, user));
        }

        if (!icons.getChildren().isEmpty()) {
            return icons;
        }
        if (linkedFiles.size() > 1) {
            return IconTheme.JabRefIcons.FILE_MULTIPLE.getGraphicNode();
        } else if (linkedFiles.size() == 1) {
            return ExternalFileTypes.getExternalFileTypeByLinkedFile(
                                            linkedFiles.getFirst(), true,
                                            preferences.getExternalApplicationsPreferences())
                                    .map(ExternalFileType::getIcon)
                                    .orElse(IconTheme.JabRefIcons.FILE)
                                    .getGraphicNode();
        } else {
            return null;
        }
    }

    private Node createBubbleNode(BibEntryTableViewModel entry,
                                  List<LinkedFile> linkedFiles,
                                  LinkedFile reference,
                                  String user) {
        Node bubble = IconTheme.JabRefIcons.FILE_COMMENT.getGraphicNode();
        bubble.setOnMouseClicked(ev -> linkedFiles.stream()
                                                  .filter(LinkedFile::isCommented)
                                                  .filter(lf -> sameBase(lf, reference)
                                                          && lf.getLink().contains(" - comments " + user + ".pdf"))
                                                  .findFirst()
                                                  .ifPresent(lf -> new LinkedFileViewModel(
                                                          lf, entry.getEntry(), database,
                                                          taskExecutor, dialogService, preferences).open()));
        return bubble;
    }

    private static boolean sameBase(LinkedFile commented, LinkedFile original) {
        String orig = FileUtil.getBaseName(Path.of(original.getLink())
                                               .getFileName()
                                               .toString());
        String comm = FileUtil.getBaseName(Path.of(commented.getLink())
                                               .getFileName()
                                               .toString())
                              .replaceAll(" - comments .*", "");
        return orig.equals(comm);
    }
}
