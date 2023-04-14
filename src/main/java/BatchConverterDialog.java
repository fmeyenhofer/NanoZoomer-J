import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Dialog to make a ordered selection among listed items.
 */
public class BatchConverterDialog extends JDialog implements ActionListener, ListSelectionListener, KeyListener {

    /** Attribute Names in the order of selection */
    private TreeMap<Integer, HTplusFluo.Channel> selection;

    /** Table with the selection order and the attribute name */
    private JTable table;

    /** JFileChooser */
    private JFileChooser fileChooser;

    /** Chosen Path */
    private final JTextField pathField;

    /** Series combobox */
    private final JComboBox<String> seriesChooser;

    /** Flag to check if the dialog was cancelled */
    private boolean cancelled = false;

    /** Window title */
    private static final String DIALOG_TITLE = "NDPI Batch Converter";

    /** Confirmation button name */
    private static final String CONFIRMATION_BUTTON_NAME = "OK";

    /** Cancel button name */
    private static final String CANCEL_BUTTON_NAME = "Cancel";

    /** Browse button name */
    private static final String BROWSE_BUTTON_NAME = "Browse";

    /** Series combobox name */
    private static final String SERIES_CHOOSER_NAME = "Pixel Size";

    /** Column names of the selection table */
    private static final String[] COLUMN_NAMES = {"Order", "Channel"};

    /** Dialog width */
    private final int WIDTH = 500;

    /** Dialog height */
    private final int HEIGHT = 300;


    private BatchConverterDialog() {
        fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        // File browser
        JPanel filePanel = new JPanel();
        filePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 5));
        pathField = new JTextField(30);
        filePanel.add(pathField);
        JButton browseButton = new JButton(BROWSE_BUTTON_NAME);
        browseButton.addActionListener(this);
        filePanel.add(browseButton);

        // Series combobox
        JPanel magPanel = new JPanel();
        magPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 5));
        magPanel.add(new JLabel(SERIES_CHOOSER_NAME));
        seriesChooser = new JComboBox<>();
        seriesChooser.setName(SERIES_CHOOSER_NAME);
        magPanel.add(seriesChooser);

        // Create the table
        DefaultTableModel model = new DefaultTableModel(new String[5][2], COLUMN_NAMES);

        table = new JTable(model) {
            @Override
            public boolean isCellEditable ( int row, int column )
            {
                return false;
            }
        };

        ListSelectionModel listSelectionModel = table.getSelectionModel();
        listSelectionModel.addListSelectionListener(this);
        table.setSelectionModel(listSelectionModel);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.addKeyListener(this);

        JScrollPane tablePanel = new JScrollPane();
        tablePanel.setToolTipText("Use Shift and Ctrl/Cmd to select multiple items.");
        tablePanel.setViewportView(table);
        tablePanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // Ok and cancel buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 5));
        JButton buttonCancel = new JButton(CANCEL_BUTTON_NAME);
        buttonCancel.addActionListener(this);
        buttonPanel.add(buttonCancel);
        JButton buttonOK = new JButton(CONFIRMATION_BUTTON_NAME);
        buttonOK.addActionListener(this);
        buttonPanel.add(buttonOK);

        // Assemble the different sections in one pane.
        this.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.insets = new Insets(7, 15, 7, 15);
        constraints.fill = GridBagConstraints.BOTH;
        this.add(filePanel, constraints);
        constraints.gridy = 1;
        this.add(magPanel, constraints);
        constraints.weighty = 1;
        constraints.gridy = 2;
        this.add(tablePanel, constraints);
        constraints.gridy = 3;
        constraints.weighty = 0;
        this.add(buttonPanel, constraints);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelled = true;
                super.windowClosing(e);
            }
        });

        this.setTitle(DIALOG_TITLE);
        this.getRootPane().setDefaultButton(buttonOK);
    }

    static BatchConverterDialog createAndShow() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

        BatchConverterDialog dialog = new BatchConverterDialog();
        dialog.setModal(true);
        dialog.setLocationByPlatform(true);
        dialog.setSize(new Dimension(dialog.WIDTH,300));
        dialog.setLocation(screen.width/2-dialog.WIDTH, screen.height/2-dialog.HEIGHT);
//        dialog.getRootPane().setDefaultButton(dialog.buttonOK);
//        dialog.buttonOK.requestFocus();
        dialog.setVisible(true);
        dialog.pack();

        return dialog;
    }

    boolean isCancelled() {
        return cancelled;
    }

    ArrayList<HTplusFluo.Channel> getSelectedChannels() {
        ArrayList<HTplusFluo.Channel> data = new ArrayList<>(selection.size());

        for (Integer index: selection.keySet()) {
            data.add(index-1, selection.get(index));
        }
        return data;
    }

    File getSelectedDirectory() {
        return new File(pathField.getText());
    }

    int getSelectedSeries() {
        return seriesChooser.getSelectedIndex();
    }

    private void selectAll() {
        for (int i = 0; i < table.getRowCount(); i++) {
            table.setValueAt(Integer.toString(i), i, 0);
        }

        ListSelectionModel model = table.getSelectionModel();
        model.setSelectionInterval(0, table.getRowCount() - 1);
        
        repaint();
    }

    private void updateSelection() {
        selection = new TreeMap<>();
        for (int i = 0; i < table.getRowCount(); i++) {
            if (!table.getValueAt(i, 0).equals("-")) {
                selection.put(Integer.parseInt((String) table.getValueAt(i, 0)),
                        (HTplusFluo.Channel) table.getValueAt(i, 1));
            }
        }
    }

    private void updateContent() {
        pathField.setText(fileChooser.getSelectedFile().getAbsolutePath());

        // Channels
        ListSelectionModel selectionModel = table.getSelectionModel();
        selectionModel.removeListSelectionListener(this);
        List<HTplusFluo.Channel> channels = NdpiUtils.getChannelList(fileChooser.getSelectedFile());

        int nRows = channels.size();

        DefaultTableModel model = new DefaultTableModel(new String[nRows][2], COLUMN_NAMES);
        table.setModel(model);

        for (int row = 0; row < nRows; row++) {
            table.setValueAt("" + (row+1), row, 0);
            table.setValueAt(channels.get(row), row, 1);
        }

        selectionModel.addListSelectionListener(this);
        selectAll();
        updateSelection();

        // Series
        try {
//            File file = NdpiUtils.getFirstFile(fileChooser.getSelectedFile());
            HashMap<HTplusFluo.Channel, List<File>> files = NdpiUtils.getFiles(fileChooser.getSelectedFile());
            if (files.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "There are no NDPI files in the selected directory.\nTry another one.");
                return;
            }

            File file = files.get(files.keySet().iterator().next()).get(0);
            List<String> pixelSizes = NdpiUtils.getSeriesPixelSizes(file);
            seriesChooser.removeAllItems();
            for (String item : pixelSizes) {
                seriesChooser.addItem(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals(BROWSE_BUTTON_NAME)) {
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                updateContent();
            }
        } else {

            if (e.getActionCommand().equals(CANCEL_BUTTON_NAME)) {
                cancelled = true;
            }

            this.dispose();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void valueChanged(ListSelectionEvent event) {
        ListSelectionModel lsm = (ListSelectionModel) event.getSource();

        // Only use one of the actions
        if (lsm.getValueIsAdjusting()){
            return;
        }

        // fetch previous index map (order, position) and the indices of the selected rows
        HashMap<String, Integer> indexMap = new HashMap<>();
        ArrayList<Integer> selectedIndices = new ArrayList<>();

        for (int i=0; i <table.getRowCount(); i++) {

            if (lsm.isSelectedIndex(i)) {
                if (!table.getValueAt(i,0).equals("-")) {
                    indexMap.put((String) table.getValueAt(i, 0), i);
                }
                selectedIndices.add(i);
            } else if (!table.getValueAt(i,0).equals("-")) {
                table.setValueAt("-", i, 0);
            }
        }

        // Sort according the order
        List<String> orders = new ArrayList<>(indexMap.keySet());
        Collections.sort(orders);

        // Re-index if one selection dropped out. Also invert keys and values for the next step
        HashMap<Integer, String> validatedIndexMap = new HashMap<>();
        int newOrder = 1;
        for (String order : orders) {
            validatedIndexMap.put(indexMap.get(order), "" + newOrder);
            newOrder++;
        }

        // Update the table
        int order = indexMap.size();
        for (Integer pos : selectedIndices) {

            if (validatedIndexMap.containsKey(pos)) {
                table.setValueAt(validatedIndexMap.get(pos), pos, 0);
            } else {
                ++order;
                table.setValueAt("" + order, pos, 0);
            }
        }

        updateSelection();

        repaint();
    }

    /** {@inheritDoc} */
    @Override
    public void keyTyped(KeyEvent e) {
        if (e.getExtendedKeyCode() == KeyEvent.VK_ENTER) {
            updateSelection();
            this.dispose();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void keyPressed(KeyEvent e) {
        // nothing
    }

    /** {@inheritDoc} */
    @Override
    public void keyReleased(KeyEvent e) {
        // nothing
    }


    /**
     * Quick testing
     *
     * @param args whatever
     */
    public static void main(String[] args) {
        BatchConverterDialog dialog =  BatchConverterDialog.createAndShow();

        System.out.println("Input path: " + dialog.getSelectedDirectory());
        System.out.println("Selected channels:  ");
        for (HTplusFluo.Channel channel : dialog.getSelectedChannels()) {
            System.out.println("\t" + channel);
        }
        System.out.println("Series index: " + dialog.getSelectedSeries());
        System.exit(0);
    }
}

