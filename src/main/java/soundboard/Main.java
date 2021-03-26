package soundboard;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileSystemView;

public class Main {
	public static final File SOUNDBOARD_FOLDER = new File(FileSystemView.getFileSystemView().getHomeDirectory(), "soundboard");
	public static final Font FONT = new Font("Arial", Font.PLAIN, 12);
	public static final Line.Info LINE_OUT = new Line.Info(SourceDataLine.class);

	public static JFrame frame;
	public static Clip clip;
	public static DefaultListModel<File> sounds;
	public static JButton changeDeviceButton;

	public static void main(String[] args) throws InterruptedException {
		SOUNDBOARD_FOLDER.mkdirs();

		frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setTitle("Bodged Java Soundboard");
		frame.setSize(600, 300);
		JPanel main = new JPanel();
		main.setLayout(new BorderLayout());

		JButton openFolderButton = new JButton("Open " + SOUNDBOARD_FOLDER.getPath());
		openFolderButton.setFont(FONT);
		openFolderButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					Desktop.getDesktop().open(SOUNDBOARD_FOLDER);
				} catch (IOException e1) {
					e1.printStackTrace();
					JOptionPane.showMessageDialog(frame, "An error occoured.", "Bodged Java Soundboard", JOptionPane.ERROR_MESSAGE);
					System.exit(1);
				}
			}
		});
		main.add(openFolderButton, BorderLayout.NORTH);

		JPanel options = new JPanel(new GridBagLayout());
		JCheckBox alwaysOnTop = new JCheckBox("Always on top");
		alwaysOnTop.setFont(FONT);
		alwaysOnTop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				frame.setAlwaysOnTop(alwaysOnTop.isSelected());
				saveSettings();
			}
		});
		options.add(alwaysOnTop);
		changeDeviceButton = new JButton("Change Output Device... (No Device selected)");
		changeDeviceButton.setFont(FONT);
		changeDeviceButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				List<String> outputs = new ArrayList<String>();
				Mixer.Info[] mixers = AudioSystem.getMixerInfo();
				for(Mixer.Info mixerInfo : mixers) {
					Mixer mixer = AudioSystem.getMixer(mixerInfo);
					if(mixer.isLineSupported(LINE_OUT)) {
						outputs.add(mixerInfo.getName());
					}
				}
				String name = (String) JOptionPane.showInputDialog(frame, "Select an Output Device: ", "Bodged Java Soundboard", JOptionPane.QUESTION_MESSAGE, null, outputs.toArray(), null);
				if(name != null) {
					changeDevice(name);
				}
				saveSettings();
			}
		});
		options.add(changeDeviceButton);
		main.add(options, BorderLayout.SOUTH);

		sounds = new DefaultListModel<File>();
		refreshSounds();
		DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				return super.getListCellRendererComponent(list, ((File) value).getName(), index, isSelected, cellHasFocus);
			}
		};
		JList<File> soundboard = new JList<File>(sounds);
		soundboard.setCellRenderer(cellRenderer);
		soundboard.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
		soundboard.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if(!e.getValueIsAdjusting() && soundboard.getSelectedValue() != null) {
					if(clip != null) {
						System.out.println(soundboard.getSelectedValue());
						try {
							clip.close();
							clip.open(AudioSystem.getAudioInputStream(soundboard.getSelectedValue()));
							clip.start();
						} catch (LineUnavailableException | IOException | UnsupportedAudioFileException e1) {
							e1.printStackTrace();
							JOptionPane.showMessageDialog(frame, "An error occoured.", "Bodged Java Soundboard", JOptionPane.ERROR_MESSAGE);
							System.exit(1);
						}
					} else JOptionPane.showMessageDialog(frame, "Select a device first!", "Bodged Java Soundboard", JOptionPane.WARNING_MESSAGE);
					soundboard.clearSelection();
				}
			}
		});
		main.add(new JScrollPane(soundboard), BorderLayout.CENTER);

		if(new File(SOUNDBOARD_FOLDER, ".settings").isFile()) {
			loadSettings();
			alwaysOnTop.setSelected(frame.isAlwaysOnTop());
		}

		frame.add(main);
		frame.setVisible(true);
		
		try {
			WatchService watcher = FileSystems.getDefault().newWatchService();
			Path dir = SOUNDBOARD_FOLDER.toPath();
			WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);

			while(true) {
				key = watcher.take();
				List<WatchEvent<?>> events = key.pollEvents();
				if(events.size() != 0) {
					System.out.println("Updating list");
					EventQueue.invokeLater(() -> {
						refreshSounds();
					});
				}
				if(!key.reset()) {
					JOptionPane.showMessageDialog(frame, "An error occoured.", "Bodged Java Soundboard", JOptionPane.ERROR_MESSAGE);
					System.exit(1);
				}
				soundboard.revalidate();
				soundboard.repaint();
				Thread.sleep(100);
			}
		} catch (IOException e1) {
			e1.printStackTrace();
			JOptionPane.showMessageDialog(frame, "An error occoured.", "Bodged Java Soundboard", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

	public static void changeDevice(String name) {
		try {
			if(clip != null && clip.isOpen()) clip.close();
			Mixer.Info[] mixers = AudioSystem.getMixerInfo();
			for(Mixer.Info mixerInfo : mixers) {
				if(mixerInfo.getName().equals(name)) {
					clip = AudioSystem.getClip(mixerInfo);
					changeDeviceButton.setText("Change Output Device... (" + name + ")");
				}
			}
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame, "An error occoured.", "Bodged Java Soundboard", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

	public static void refreshSounds() {
		sounds.removeAllElements();
		for(File file : SOUNDBOARD_FOLDER.listFiles()) {
			if(file.getName().endsWith(".wav")) sounds.add(sounds.getSize(), file);
		}
	}

	public static void loadSettings() {
		try {
			File settings = new File(SOUNDBOARD_FOLDER, ".settings");
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(settings)));
			changeDevice(br.readLine());
			frame.setAlwaysOnTop(Boolean.parseBoolean(br.readLine()));
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame, "An error occoured.", "Bodged Java Soundboard", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

	}

	public static void saveSettings() {
		try {
			File settings = new File(SOUNDBOARD_FOLDER, ".settings");
			if(settings.exists()) settings.delete();
			settings.createNewFile();
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(settings)));
			bw.write(changeDeviceButton.getText().substring(25, changeDeviceButton.getText().length() - 1) + "\r\n");
			bw.write(frame.isAlwaysOnTop() + "\r\n");
			bw.flush();
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}