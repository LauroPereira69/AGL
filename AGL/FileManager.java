package AGL;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.*; // Usado no recurso de arrastar e soltar arquivos
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Classe principal do sistema.
 */
public class FileManager extends JFrame {

    private JTable fileTable;
    private FileTableModel tableModel;
    private JTextField pathField;
    private JTextField searchField;
    private JTextArea detailsArea;
    private JList<File> sideList;
    private DefaultListModel<File> sideModel;

    private File currentDirectory;
    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private final Stack<File> backHistory = new Stack<>();
    private final Stack<File> forwardHistory = new Stack<>();

    // Arquivo que foi copiado ou recortado usando os botões, menu ou atalhos.
    private File clipboardFile;

    // Indica se a operação atual é recortar. false = copiar, true = recortar.
    private boolean cutOperation = false;

    private JButton backButton;
    private JButton forwardButton;
    private JButton upButton;

    // Construtor: é executado quando a janela do programa é criada.
    public FileManager() {
        configurarLookAndFeel();
        configurarJanela();
        currentDirectory = fileSystemView.getHomeDirectory();
        criarInterface();
        navigateTo(currentDirectory, false);
    }

    // Configura tamanho, título e comportamento da janela principal.
    private void configurarJanela() {
        setTitle("AGL Explorador de Arquivos");
        setSize(1050, 650);
        setMinimumSize(new Dimension(900, 550));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    // Aplica o visual nativo do sistema operacional, deixando mais parecido com o Windows.
    private void configurarLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Table.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("Button.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 13));
        } catch (Exception ignored) {
        }
    }

    // Monta toda a tela: barra superior, lateral, tabela central, detalhes e botões inferiores.
    private void criarInterface() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        backButton = criarBotao("←", "Voltar");
        forwardButton = criarBotao("→", "Avançar");
        upButton = criarBotao("↑", "Subir um nível");

        backButton.addActionListener(e -> voltar());
        forwardButton.addActionListener(e -> avancar());
        upButton.addActionListener(e -> subir());

        navPanel.add(backButton);
        navPanel.add(forwardButton);
        navPanel.add(upButton);

        pathField = new JTextField();
        pathField.addActionListener(e -> {
            File destino = new File(pathField.getText());
            if (destino.exists() && destino.isDirectory()) {
                navigateTo(destino, true);
            } else {
                mensagem("Caminho inválido.");
            }
        });

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(220, 30));
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                atualizarTabela();
            }
        });

        topPanel.add(navPanel, BorderLayout.WEST);
        topPanel.add(pathField, BorderLayout.CENTER);
        topPanel.add(searchField, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        criarBarraLateral();
        criarTabelaArquivos();
        criarPainelDetalhes();

        JSplitPane centro = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(sideList),
                new JScrollPane(fileTable)
        );
        centro.setDividerLocation(210);

        JSplitPane principal = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                centro,
                new JScrollPane(detailsArea)
        );
        principal.setDividerLocation(780);

        add(principal, BorderLayout.CENTER);
        add(criarBarraAcoes(), BorderLayout.SOUTH);
    }

    private JButton criarBotao(String texto, String dica) {
        JButton botao = new JButton(texto);
        botao.setToolTipText(dica);
        botao.setFocusPainted(false);
        botao.setPreferredSize(new Dimension(42, 30));
        return botao;
    }

    // Cria a barra lateral com atalhos para pastas comuns e discos do computador.
    private void criarBarraLateral() {
        sideModel = new DefaultListModel<>();

        adicionarAtalho(fileSystemView.getHomeDirectory());
        adicionarAtalho(new File(System.getProperty("user.home"), "Desktop"));
        adicionarAtalho(new File(System.getProperty("user.home"), "Documents"));
        adicionarAtalho(new File(System.getProperty("user.home"), "Downloads"));

        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                adicionarAtalho(root);
            }
        }

        sideList = new JList<>(sideModel);
        sideList.setFixedCellHeight(34);
        sideList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sideList.setCellRenderer(new SideRenderer());

        sideList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    File item = sideList.getSelectedValue();
                    if (item != null && item.exists()) {
                        navigateTo(item, true);
                    }
                }
            }
        });
    }

    private void adicionarAtalho(File file) {
        if (file != null && file.exists()) {
            sideModel.addElement(file);
        }
    }

    // Cria a tabela principal onde os arquivos e pastas são exibidos.
    private void criarTabelaArquivos() {
        tableModel = new FileTableModel();
        fileTable = new JTable(tableModel);
        fileTable.setRowHeight(28);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setAutoCreateRowSorter(true);
        fileTable.setShowGrid(false);
        fileTable.setIntercellSpacing(new Dimension(0, 0));
        fileTable.setFillsViewportHeight(true);

        fileTable.getColumnModel().getColumn(0).setPreferredWidth(330);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(90);

        fileTable.getColumnModel().getColumn(0).setCellRenderer(new FileNameRenderer());

        // Ativa o recurso de clicar e arrastar arquivos com o mouse.
        configurarArrastarESoltar();

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                atualizarDetalhes();

                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    abrirSelecionado();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                verificarMenuContexto(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                verificarMenuContexto(e);
            }

            private void verificarMenuContexto(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }

                int row = fileTable.rowAtPoint(e.getPoint());

                if (row >= 0) {
                    fileTable.setRowSelectionInterval(row, row);
                    criarMenuArquivo().show(fileTable, e.getX(), e.getY());
                } else {
                    fileTable.clearSelection();
                    criarMenuAreaVazia().show(fileTable, e.getX(), e.getY());
                }
            }
        });

        fileTable.getSelectionModel().addListSelectionListener(e -> atualizarDetalhes());
        configurarAtalhosTeclado();
    }

    // Configura atalhos de teclado parecidos com o Explorador do Windows.
    private void configurarAtalhosTeclado() {
        fileTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        fileTable.getActionMap().put("delete", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                deletarSelecionado();
            }
        });

        fileTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "rename");
        fileTable.getActionMap().put("rename", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                renomearSelecionado();
            }
        });

        fileTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        fileTable.getActionMap().put("open", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                abrirSelecionado();
            }
        });

        fileTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        fileTable.getActionMap().put("copy", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                copiarSelecionado(false);
            }
        });

        fileTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), "cut");
        fileTable.getActionMap().put("cut", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                copiarSelecionado(true);
            }
        });

        fileTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        fileTable.getActionMap().put("paste", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                colarArquivo();
            }
        });
    }

    private void criarPainelDetalhes() {
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        detailsArea.setBackground(new Color(248, 248, 248));
        detailsArea.setBorder(new EmptyBorder(10, 10, 10, 10));
    }

    private JPanel criarBarraAcoes() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        JButton novoArquivo = new JButton("Novo arquivo");
        JButton novaPasta = new JButton("Nova pasta");
        JButton renomear = new JButton("Renomear");
        JButton excluir = new JButton("Excluir");
        JButton copiar = new JButton("Copiar");
        JButton recortar = new JButton("Recortar");
        JButton colar = new JButton("Colar");
        // Botão extra para mover o arquivo de forma simples, sem depender de copiar/colar.
        JButton mover = new JButton("Mover");
        JButton atualizar = new JButton("Atualizar");

        novoArquivo.addActionListener(e -> criarNovo(false));
        novaPasta.addActionListener(e -> criarNovo(true));
        renomear.addActionListener(e -> renomearSelecionado());
        excluir.addActionListener(e -> deletarSelecionado());
        copiar.addActionListener(e -> copiarSelecionado(false));
        recortar.addActionListener(e -> copiarSelecionado(true));
        colar.addActionListener(e -> colarArquivo());
        // Ao clicar em Mover, o usuário escolhe uma pasta e o item selecionado é movido para lá.
        mover.addActionListener(e -> moverSelecionadoComEscolha());
        atualizar.addActionListener(e -> atualizarTabela());

        panel.add(novoArquivo);
        panel.add(novaPasta);
        panel.add(renomear);
        panel.add(excluir);
        panel.add(copiar);
        panel.add(recortar);
        panel.add(colar);
        panel.add(mover);
        panel.add(atualizar);

        return panel;
    }

    // Menu que aparece quando o usuário clica com o botão direito em cima de um arquivo ou pasta.
    private JPopupMenu criarMenuArquivo() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem abrir = new JMenuItem("Abrir");
        JMenuItem copiar = new JMenuItem("Copiar");
        JMenuItem recortar = new JMenuItem("Recortar");
        JMenuItem colar = new JMenuItem("Colar");
        // Opção direta para mover, mais fácil para apresentação e para o usuário.
        JMenuItem mover = new JMenuItem("Mover para...");
        JMenuItem renomear = new JMenuItem("Renomear");
        JMenuItem excluir = new JMenuItem("Excluir");
        JMenuItem propriedades = new JMenuItem("Propriedades");

        abrir.addActionListener(e -> abrirSelecionado());
        copiar.addActionListener(e -> copiarSelecionado(false));
        recortar.addActionListener(e -> copiarSelecionado(true));
        colar.addActionListener(e -> colarArquivo());
        mover.addActionListener(e -> moverSelecionadoComEscolha());
        renomear.addActionListener(e -> renomearSelecionado());
        excluir.addActionListener(e -> deletarSelecionado());
        propriedades.addActionListener(e -> mostrarPropriedades());

        menu.add(abrir);
        menu.addSeparator();
        menu.add(copiar);
        menu.add(recortar);
        menu.add(colar);
        menu.add(mover);
        menu.addSeparator();
        menu.add(renomear);
        menu.add(excluir);
        menu.addSeparator();
        menu.add(propriedades);

        return menu;
    }

    // Menu que aparece quando o usuário clica com o botão direito em uma área vazia da tabela.
    private JPopupMenu criarMenuAreaVazia() {
        JPopupMenu menu = new JPopupMenu();

        JMenu novo = new JMenu("Novo");

        JMenuItem novoArquivo = new JMenuItem("Arquivo");
        JMenuItem novaPasta = new JMenuItem("Pasta");
        JMenuItem colar = new JMenuItem("Colar");
        JMenuItem atualizar = new JMenuItem("Atualizar");

        novoArquivo.addActionListener(e -> criarNovo(false));
        novaPasta.addActionListener(e -> criarNovo(true));
        colar.addActionListener(e -> colarArquivo());
        atualizar.addActionListener(e -> atualizarTabela());

        novo.add(novoArquivo);
        novo.add(novaPasta);

        menu.add(novo);
        menu.addSeparator();
        menu.add(colar);
        menu.addSeparator();
        menu.add(atualizar);

        return menu;
    }

    // Navega para outro diretório e, se necessário, salva o diretório anterior no histórico.
    private void navigateTo(File diretorio, boolean salvarHistorico) {
        if (diretorio == null || !diretorio.exists() || !diretorio.isDirectory()) {
            mensagem("Diretório inválido.");
            return;
        }

        if (salvarHistorico && currentDirectory != null && !currentDirectory.equals(diretorio)) {
            backHistory.push(currentDirectory);
            forwardHistory.clear();
        }

        currentDirectory = diretorio;
        pathField.setText(currentDirectory.getAbsolutePath());
        searchField.setText("");
        atualizarTabela();
        atualizarBotoes();
    }

    private void voltar() {
        if (!backHistory.isEmpty()) {
            forwardHistory.push(currentDirectory);
            currentDirectory = backHistory.pop();
            navigateTo(currentDirectory, false);
        }
    }

    private void avancar() {
        if (!forwardHistory.isEmpty()) {
            backHistory.push(currentDirectory);
            currentDirectory = forwardHistory.pop();
            navigateTo(currentDirectory, false);
        }
    }

    private void subir() {
        File pai = currentDirectory.getParentFile();
        if (pai != null) {
            navigateTo(pai, true);
        }
    }

    private void atualizarBotoes() {
        backButton.setEnabled(!backHistory.isEmpty());
        forwardButton.setEnabled(!forwardHistory.isEmpty());
        upButton.setEnabled(currentDirectory != null && currentDirectory.getParentFile() != null);
    }

    // Atualiza a tabela com os arquivos da pasta atual, aplicando também o filtro de pesquisa.
    private void atualizarTabela() {
        File[] arquivos = currentDirectory.listFiles();
        List<File> lista = new ArrayList<>();

        String filtro = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();

        if (arquivos != null) {
            Arrays.sort(arquivos, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File arquivo : arquivos) {
                if (filtro.isEmpty() || arquivo.getName().toLowerCase().contains(filtro)) {
                    lista.add(arquivo);
                }
            }
        }

        tableModel.setFiles(lista);
        detailsArea.setText("Itens: " + lista.size() + "\nPasta atual:\n" + currentDirectory.getAbsolutePath());
    }

    // Retorna o arquivo selecionado na tabela.
    private File getSelecionado() {
        int row = fileTable.getSelectedRow();

        if (row < 0) {
            return null;
        }

        int modelRow = fileTable.convertRowIndexToModel(row);
        return tableModel.getFileAt(modelRow);
    }

    private void abrirSelecionado() {
        File file = getSelecionado();

        if (file == null) {
            mensagem("Nenhum item selecionado.");
            return;
        }

        if (file.isDirectory()) {
            navigateTo(file, true);
        } else {
            abrirArquivo(file);
        }
    }

    private void abrirArquivo(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            } else {
                mensagem("Abertura de arquivo não suportada neste sistema.");
            }
        } catch (IOException ex) {
            mensagem("Erro ao abrir arquivo: " + ex.getMessage());
        }
    }

    // Cria um novo arquivo ou uma nova pasta dentro do diretório atual.
    private void criarNovo(boolean pasta) {
        String tipo = pasta ? "pasta" : "arquivo";
        String nome = JOptionPane.showInputDialog(this, "Nome da nova " + tipo + ":");

        if (nome == null || nome.trim().isEmpty()) {
            return;
        }

        File novo = new File(currentDirectory, nome.trim());

        try {
            boolean criado;

            if (pasta) {
                criado = novo.mkdir();
            } else {
                criado = novo.createNewFile();
            }

            if (criado) {
                atualizarTabela();
            } else {
                mensagem("Não foi possível criar. Talvez já exista um item com esse nome.");
            }
        } catch (IOException ex) {
            mensagem("Erro ao criar: " + ex.getMessage());
        }
    }

    // Renomeia o arquivo ou pasta selecionado.
    private void renomearSelecionado() {
        File file = getSelecionado();

        if (file == null) {
            mensagem("Nenhum item selecionado.");
            return;
        }

        String novoNome = JOptionPane.showInputDialog(this, "Novo nome:", file.getName());

        if (novoNome == null || novoNome.trim().isEmpty()) {
            return;
        }

        File novoArquivo = new File(currentDirectory, novoNome.trim());

        if (file.renameTo(novoArquivo)) {
            atualizarTabela();
        } else {
            mensagem("Não foi possível renomear.");
        }
    }

    // Exclui o arquivo ou pasta selecionado, pedindo confirmação antes.
    private void deletarSelecionado() {
        File file = getSelecionado();

        if (file == null) {
            mensagem("Nenhum item selecionado.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Deseja excluir este item?\n" + file.getName(),
                "Confirmar exclusão",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            deletarRecursivo(file.toPath());
            atualizarTabela();
        } catch (IOException ex) {
            mensagem("Erro ao excluir: " + ex.getMessage());
        }
    }

    // Exclui arquivos e pastas. Se for pasta, exclui também tudo que existe dentro dela.
    private void deletarRecursivo(Path path) throws IOException {
        if (!Files.exists(path)) return;

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // Salva o arquivo selecionado na área de transferência interna do programa.
    private void copiarSelecionado(boolean recortar) {
        File file = getSelecionado();

        if (file == null) {
            mensagem("Nenhum item selecionado.");
            return;
        }

        clipboardFile = file;
        cutOperation = recortar;

        String acao = recortar ? "recortado" : "copiado";
        detailsArea.setText("Item " + acao + ":\n" + file.getAbsolutePath());
    }

    // Cola o arquivo copiado ou recortado na pasta atual.
    private void colarArquivo() {
        if (clipboardFile == null || !clipboardFile.exists()) {
            mensagem("Nenhum arquivo copiado ou recortado.");
            return;
        }

        Path origem = clipboardFile.toPath();
        Path destino = currentDirectory.toPath().resolve(clipboardFile.getName());

        // Evita erro quando o usuário tenta colar o arquivo na mesma pasta de origem.
        if (origem.equals(destino)) {
            mensagem("O arquivo já está nesta pasta.");
            return;
        }

        try {
            if (clipboardFile.isDirectory()) {
                copiarDiretorio(origem, destino);

                if (cutOperation) {
                    deletarRecursivo(origem);
                }
            } else {
                if (cutOperation) {
                    Files.move(origem, destino, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.copy(origem, destino, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            if (cutOperation) {
                clipboardFile = null;
            }

            atualizarTabela();
        } catch (IOException ex) {
            mensagem("Erro ao colar: " + ex.getMessage());
        }
    }

    /**
     * Move o arquivo ou pasta selecionado para uma pasta escolhida pelo usuário.
     */
    private void moverSelecionadoComEscolha() {
        File selecionado = getSelecionado();

        // Se nada estiver selecionado, não há o que mover.
        if (selecionado == null) {
            mensagem("Nenhum item selecionado para mover.");
            return;
        }

        // Abre uma janela para o usuário escolher somente diretórios.
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Escolha a pasta de destino");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        int opcao = chooser.showOpenDialog(this);

        // Se o usuário cancelar, nada é feito.
        if (opcao != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File pastaDestino = chooser.getSelectedFile();

        try {
            // Reaproveita o método usado pelo arrastar e soltar.
            moverArquivoParaDiretorio(selecionado, pastaDestino);

            // Atualiza a tabela para refletir a mudança.
            atualizarTabela();

            // Mostra uma confirmação simples para o usuário.
            mensagem("Item movido com sucesso.");

        } catch (IOException ex) {
            mensagem("Erro ao mover item: " + ex.getMessage());
        }
    }

    // Copia uma pasta inteira, incluindo subpastas e arquivos internos.
    private void copiarDiretorio(Path origem, Path destino) throws IOException {
        Files.walkFileTree(origem, new SimpleFileVisitor<Path>() {    // Percorre toda a arvore de pastas do diretorio
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException { //executado sempre que o programa encontra uma nova pasta antes de entrar nela
                Path alvo = destino.resolve(origem.relativize(dir));

                if (!Files.exists(alvo)) {
                    Files.createDirectories(alvo);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destino.resolve(origem.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }



    /**
     * Configura o recurso de arrastar e soltar arquivos.
     */
    private void configurarArrastarESoltar() {
        // Permite que a JTable inicie operações de arrastar.
        fileTable.setDragEnabled(true);

        // Define que o usuário deve soltar o item em cima de uma linha da tabela.
        fileTable.setDropMode(DropMode.ON_OR_INSERT);

        // TransferHandler é a classe do Swing responsável por controlar Drag and Drop.
        fileTable.setTransferHandler(new TransferHandler() {

            // Define qual tipo de ação será feita. Neste caso, mover arquivo.
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            // Cria o objeto que será transportado durante o arrastar.
            @Override
            protected Transferable createTransferable(JComponent c) {
                File selecionado = getSelecionado();

                // Se nada estiver selecionado, não existe o que arrastar.
                if (selecionado == null) {
                    return null;
                }

                // O Java usa uma lista de arquivos para transferir dados no Drag and Drop.
                java.util.List<File> arquivos = new ArrayList<>();
                arquivos.add(selecionado);

                // Retorna uma classe simples que representa essa lista de arquivos.
                return new ArquivosTransferable(arquivos);
            }

            // Verifica se o local onde o usuário está soltando aceita arquivos.
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            // Executa a ação quando o usuário solta o arquivo.
            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }

                try {
                    // Recupera a lista de arquivos arrastados.
                    java.util.List<File> arquivos = (java.util.List<File>) support
                            .getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    if (arquivos == null || arquivos.isEmpty()) {
                        return false;
                    }

                    // Identifica em qual linha da tabela o usuário soltou o arquivo.
                    JTable.DropLocation dropLocation = (JTable.DropLocation) support.getDropLocation();
                    int linhaDestino = dropLocation.getRow();

                    File pastaDestino = currentDirectory;

                    // Se o usuário soltou em cima de uma linha, verifica se essa linha é uma pasta.
                    if (linhaDestino >= 0 && linhaDestino < fileTable.getRowCount()) {
                        int linhaModelo = fileTable.convertRowIndexToModel(linhaDestino);
                        File itemDestino = tableModel.getFileAt(linhaModelo);

                        // Só permite soltar em cima de pastas.
                        if (itemDestino != null && itemDestino.isDirectory()) {
                            pastaDestino = itemDestino;
                        } else {
                            mensagem("Solte o arquivo em cima de uma pasta.");
                            return false;
                        }
                    }

                    // Move cada arquivo arrastado para a pasta de destino.
                    for (File arquivo : arquivos) {
                        moverArquivoParaDiretorio(arquivo, pastaDestino);
                    }

                    // Atualiza a tabela para mostrar as mudanças.
                    atualizarTabela();
                    return true;

                } catch (Exception ex) {
                    mensagem("Erro ao arrastar arquivo: " + ex.getMessage());
                    return false;
                }
            }
        });
    }

    /**
     * Move um arquivo ou pasta para dentro de outro diretório.
     * Esse método é usado pela função de arrastar e soltar.
     */
    private void moverArquivoParaDiretorio(File origem, File pastaDestino) throws IOException {
        // Validação básica para evitar erro caso o arquivo não exista.
        if (origem == null || !origem.exists()) {
            return;
        }

        // Garante que o destino seja realmente uma pasta.
        if (pastaDestino == null || !pastaDestino.exists() || !pastaDestino.isDirectory()) {
            mensagem("Destino inválido.");
            return;
        }

        // Evita mover uma pasta para dentro dela mesma.
        if (origem.equals(pastaDestino)) {
            mensagem("Não é possível mover uma pasta para dentro dela mesma.");
            return;
        }

        // Evita mover uma pasta para dentro de uma subpasta dela mesma.
        if (origem.isDirectory() && pastaDestino.toPath().startsWith(origem.toPath())) {
            mensagem("Não é possível mover uma pasta para dentro de uma subpasta dela mesma.");
            return;
        }

        Path origemPath = origem.toPath();
        Path destinoPath = pastaDestino.toPath().resolve(origem.getName());

        // Se o arquivo já está naquela pasta, não precisa fazer nada.
        if (origemPath.equals(destinoPath)) {
            return;
        }

        // Se já existir um arquivo com o mesmo nome, pede confirmação para substituir.
        if (Files.exists(destinoPath)) {
            int opcao = JOptionPane.showConfirmDialog(
                    this,
                    "Já existe um item com este nome no destino. Deseja substituir?\n" + origem.getName(),
                    "Substituir arquivo",
                    JOptionPane.YES_NO_OPTION
            );

            if (opcao != JOptionPane.YES_OPTION) {
                return;
            }

            // Remove o item existente antes de mover o novo.
            deletarRecursivo(destinoPath);
        }

        // Move o arquivo ou pasta para o destino escolhido.
        Files.move(origemPath, destinoPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Classe usada para transportar arquivos no Drag and Drop.
     * Ela informa ao Java que o conteúdo arrastado é uma lista de arquivos.
     */
    private static class ArquivosTransferable implements Transferable {

        // Lista de arquivos que estão sendo arrastados.
        private final java.util.List<File> arquivos;

        // Construtor que recebe os arquivos a serem transportados.
        public ArquivosTransferable(java.util.List<File> arquivos) {
            this.arquivos = arquivos;
        }

        // Informa quais tipos de dados essa transferência suporta.
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
        }

        // Verifica se o tipo de dado solicitado é uma lista de arquivos.
        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        // Retorna os arquivos que estão sendo arrastados.
        @Override
        public Object getTransferData(DataFlavor flavor) {
            return arquivos;
        }
    }

    // Atualiza os detalhes do arquivo selecionado no painel lateral.
    private void atualizarDetalhes() {
        File file = getSelecionado();

        if (file == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Nome: ").append(file.getName()).append("\n");
        sb.append("Tipo: ").append(file.isDirectory() ? "Pasta" : fileSystemView.getSystemTypeDescription(file)).append("\n");
        sb.append("Caminho: ").append(file.getAbsolutePath()).append("\n");
        sb.append("Tamanho: ").append(formatarTamanho(file)).append("\n");
        sb.append("Modificado em: ")
                .append(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(file.lastModified())))
                .append("\n\n");

        sb.append("Permissões\n");
        sb.append("Leitura: ").append(file.canRead() ? "Sim" : "Não").append("\n");
        sb.append("Escrita: ").append(file.canWrite() ? "Sim" : "Não").append("\n");
        sb.append("Execução: ").append(file.canExecute() ? "Sim" : "Não").append("\n");

        detailsArea.setText(sb.toString());
    }

    // Abre uma janela com as propriedades completas do item selecionado.
    private void mostrarPropriedades() {
        File file = getSelecionado();

        if (file == null) {
            mensagem("Nenhum item selecionado.");
            return;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Nome: ").append(file.getName()).append("\n");
        sb.append("Tipo: ").append(file.isDirectory() ? "Pasta" : fileSystemView.getSystemTypeDescription(file)).append("\n");
        sb.append("Local: ").append(file.getParent()).append("\n");
        sb.append("Caminho completo: ").append(file.getAbsolutePath()).append("\n");
        sb.append("Tamanho: ").append(formatarTamanho(file)).append("\n");
        sb.append("Última modificação: ")
                .append(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date(file.lastModified())))
                .append("\n\n");

        sb.append("Permissões:\n");
        sb.append("Pode ler: ").append(file.canRead() ? "Sim" : "Não").append("\n");
        sb.append("Pode escrever: ").append(file.canWrite() ? "Sim" : "Não").append("\n");
        sb.append("Pode executar: ").append(file.canExecute() ? "Sim" : "Não").append("\n");
        sb.append("Oculto: ").append(file.isHidden() ? "Sim" : "Não").append("\n");

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setBorder(new EmptyBorder(10, 10, 10, 10));

        JOptionPane.showMessageDialog(
                this,
                new JScrollPane(area),
                "Propriedades - " + file.getName(),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    // Formata o tamanho do arquivo de bytes para KB, MB ou GB.
    private String formatarTamanho(File file) {
        if (file.isDirectory()) {
            return "";
        }

        long bytes = file.length();

        if (bytes < 1024) return bytes + " B";

        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);

        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);

        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.1f GB", gb);
    }

    private void mensagem(String texto) {
        JOptionPane.showMessageDialog(this, texto);
    }

    private class FileTableModel extends AbstractTableModel {
        private final String[] colunas = {"Nome", "Data de modificação", "Tipo", "Tamanho"};
        private List<File> files = new ArrayList<>();

        public void setFiles(List<File> files) {
            this.files = files;
            fireTableDataChanged();
        }

        public File getFileAt(int row) {
            return files.get(row);
        }

        public int getRowCount() {
            return files.size();
        }

        public int getColumnCount() {
            return colunas.length;
        }

        public String getColumnName(int column) {
            return colunas[column];
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            File file = files.get(rowIndex);

            switch (columnIndex) {
                case 0:
                    return file;

                case 1:
                    return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(file.lastModified()));

                case 2:
                    return file.isDirectory() ? "Pasta" : fileSystemView.getSystemTypeDescription(file);

                case 3:
                    return formatarTamanho(file);

                default:
                    return "";
            }
        }
    }

    private class FileNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table,
                    "",
                    isSelected,
                    hasFocus,
                    row,
                    column
            );

            if (value instanceof File) {
                File file = (File) value;
                label.setText(fileSystemView.getSystemDisplayName(file));
                label.setIcon(fileSystemView.getSystemIcon(file));
                label.setBorder(new EmptyBorder(0, 6, 0, 0));
            }

            return label;
        }
    }

    private class SideRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
            );

            if (value instanceof File) {
                File file = (File) value;
                String nome = fileSystemView.getSystemDisplayName(file);

                if (nome == null || nome.trim().isEmpty()) {
                    nome = file.getAbsolutePath();
                }

                label.setText(nome);
                label.setIcon(fileSystemView.getSystemIcon(file));
                label.setBorder(new EmptyBorder(0, 8, 0, 0));
            }

            return label;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileManager().setVisible(true));
    }
}