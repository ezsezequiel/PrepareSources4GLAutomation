package br.ezs.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

public class PrepareSource4GL {

	public static void main(String[] args) {

		try {

			File local = null;

			do {
				local = new File(JOptionPane.showInputDialog("Informe um fonte 4GL ou um diretório:"));
			} while (!local.isFile() && !local.isDirectory());

			File[] files = null;

			if (local.isFile()) {
				files = new File[1];
				files[0] = local;
			} else if (local.isDirectory()) {
				files = local.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.toLowerCase().endsWith(".4gl");
					}
				});
			}

			if (files.length == 0)
				throw new Exception("Não existem arquivos 4gl no diretório: " + local);

			showMsg("Encontrou " + files.length + " arquivos 4gl." + "\n" +
					"Pressione ok para continuar!");

			for (File file : files) {
				String sourceName = file.getName().toLowerCase().replace(".4gl", "");
				String localSource = file.getParent();

				// O processamento foi dividido em duas etapas
				// 1º Insere os before fields, before row e before inputs
				// 2º Insere os ON KEYS
				Thread thread = new Thread(new SourceService() {

					@Override
					public void logic() {
						try {
							read(localSource + "\\" + sourceName + ".4gl");
							indentify();
							prepareInputAreasOnKey();
							write(sourceName + "_temp");
						} catch (IOException e) {
							e.printStackTrace();
							System.exit(1);
						}
					}
				});

				thread.start();

				Thread thread2 = new Thread(new SourceService() {

					@Override
					public void logic() {
						try {
							read(localSource + "\\" + sourceName + "_temp.4gl");
							indentify();
							prepareInputAreas();
							createFunctionSaveFieldName();
							createFunctionShowInputName();
							createFunctionShowFieldName();
							createFunctionGetFieldName();
							write("new_" + sourceName);
							new File(localSource + "\\" + sourceName + "_temp.4gl").delete();
						} catch (IOException e) {
							e.printStackTrace();
							System.exit(1);
						}
					}
				});

				thread.join();
				thread2.start();
				thread2.join();
			}
			

		} catch (Exception e) {
			if (e.getMessage() == null)
				System.exit(0);
			showMsg("ERROR: " + e.getMessage());
		}

	}
	
	public static void showMsg(String msg){
		JOptionPane.showMessageDialog(null, msg);
	}
	
	public static List<String> errorsList = new ArrayList<String>();
}

abstract class SourceService implements Runnable {

	Source source = new Source();
	File file4GL = null;
	List<String> errors = new ArrayList<>();

	public abstract void logic();

	@Override
	public void run() {
		synchronized (this) {
			logic();
		}
	}

	public void read(String uriFile) throws IOException {
		file4GL = new File(uriFile);
		source.name = new File(uriFile).getName().toLowerCase().replace(".4gl", "").replace("_temp", "");
		BufferedReader bf = new BufferedReader(new FileReader(file4GL));

		String line = null;
		while ((line = bf.readLine()) != null)
			source.lines.add(line);
		bf.close();
	}

	public void indentify() {

		for (int idx = 0, c = source.lines.size(); idx < c; idx++) {

			try {

				source.lineContentOriginal = source.lines.get(idx);
				source.line = removeComment(source.lineContentOriginal).toUpperCase().trim();

				if (source.line.isEmpty()) {
				}

				else if (startsWith("INPUT ARRAY") || startsWith("DISPLAY ARRAY")) {
					source.inputs
							.add(new WindowArea(getArrayName(), WindowArea.WINDOW_TYPE_INPUT_ARRAY_OR_DISPLAY_ARRAY));
				}

				else if (startsWith("INPUT BY NAME") || startsWith("INPUT") && !startsWith("INPUT ARRAY")) {
					source.inputs.add(new WindowArea(getInputName(), WindowArea.WINDOW_TYPE_INPUT_OR_INPUT_BY_NAME));
				}

				else if (startsWith("BEFORE ROW"))
					source.lastInput().beforeRowExists = true;

				else if (startsWith("AFTER ROW"))
					source.lastInput().lineAfterRow = idx + 1;

				else if (startsWith("BEFORE INPUT"))
					source.lastInput().beforeInputExists = true;

				else if (startsWith("BEFORE FIELD")) {
					Field field = new Field();
					field.beforeFieldExists = true;
					field.name = getFieldName();
					source.lastInput().fields.add(field);
				}

				else if (startsWith("AFTER FIELD")) {
					if (source.lastInput().fields.size() > 0 && source.lastInput().lastField().beforeFieldExists
							&& source.lastInput().lastField().lineNumberAfterField == 0) {
						source.lastInput().lastField().lineNumberAfterField = idx + 1;
						source.lastInput().lastField().afterFieldIdentation = getIdentation();
					} else {
						Field field = new Field();
						field.lineNumberAfterField = idx + 1;
						field.name = getFieldName();
						field.afterFieldIdentation = getIdentation();
						source.lastInput().fields.add(field);
					}
				}

				else if (startsWith("ON KEY") && source.lastInput().lineFirstOnKey == 0) {
					source.lastInput().lineFirstOnKey = (idx + 1);
					source.lastInput().onKeyIdentation = getIdentation();
				}

			} catch (Exception e) {
				System.out.println("falha ao processar a linha " + (idx + 1) + " do arquivo " + source.name);
				e.printStackTrace();
				System.exit(0);
			}
		}
	}

	public boolean startsWith(String s) {
		String[] sa = s.split(" ");
		return source.line.startsWith(s)
				|| source.line.startsWith(sa[0]) && source.line.replace(sa[0], "").trim().startsWith(sa[1]);
	}

	public String getFieldName() {
		String[] sa = source.line.split(" ");
		return sa[sa.length - 1].toLowerCase();
	}

	public String getArrayName() {
		String s = source.line.split(" ")[2].toLowerCase();
		return s.contains("[") ? s.substring(0, s.indexOf("[")) : s.contains(".") ? s.substring(0, s.indexOf(".")) : s;
	}

	public String getInputName() {
		String[] sa = source.line.split(" ");
		return (startsWith("INPUT BY NAME") ? sa[3] : sa[1]).toLowerCase();
	}

	public String getIdentation() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; source.lineContentOriginal.charAt(i) == ' '; i++)
			sb.append(" ");
		return sb.toString();
	}

	public String getBlankSpace(int tam) {
		StringBuilder sb = new StringBuilder(tam);
		for (int i = 0; i < tam; i++)
			sb.append(" ");
		return sb.toString();
	}

	public String removeComment(String s) {
		int temp;
		return s.substring(0, (temp = s.indexOf("#")) != -1 ? temp : s.length());
	}

	public void prepareInputAreas() {
		int lastIndex = 0;
		for (WindowArea area : source.inputs) {
			try {
				if (area.lineFirstOnKey == 0) {
					errors.add("Área de input " + area.windowName + " não existe on key");
				} else {
					if (area.fields.size() == 0) {
						source.linesTemp.addAll(source.lines.subList(lastIndex,
								lastIndex = ((area.lineAfterRow != 0 ? area.lineAfterRow : area.lineFirstOnKey) - 1)));
						switch (area.windowType) {
						case WindowArea.WINDOW_TYPE_INPUT_ARRAY_OR_DISPLAY_ARRAY:
							source.linesTemp.addAll(createBlockCodeBeforeRow(source.name, area));
							break;
						case WindowArea.WINDOW_TYPE_INPUT_OR_INPUT_BY_NAME:
							source.linesTemp.addAll(createBlockCodeBeforeInput(source.name, area));
							break;
						}
					} else {
						for (Field field : area.fields) {
							source.linesTemp.addAll(
									source.lines.subList(lastIndex, lastIndex = field.lineNumberAfterField - 1));
							source.linesTemp.addAll(createBlockCodeBeforeField(source.name, field));
						}
					}
				}

			} catch (Exception e) {
				System.out.println("Falha ao preparar o fonte. window: " + area.windowName);
				e.printStackTrace();
				System.exit(0);
			}
		}

		refresh(lastIndex);

	}

	public void prepareInputAreasOnKey() {
		int lastIndex = 0;
		for (WindowArea area : source.inputs) {
			try {
				if (area.lineFirstOnKey != 0) {
					source.linesTemp.addAll(source.lines.subList(lastIndex, lastIndex = area.lineFirstOnKey - 1));
					source.linesTemp.addAll(createBlockCodeOnKey(source.name, area));
				}
			} catch (Exception e) {
				System.out.println("Falha ao preparar o fonte. window: " + area.windowName);
				e.printStackTrace();
				System.exit(0);
			}
		}

		refresh(lastIndex);

	}

	public int refresh(int lastIndex) {
		source.linesTemp.addAll(source.lines.subList(lastIndex, source.lines.size()));
		source.linesOutput = new ArrayList<String>(source.linesTemp);
		source.lines = new ArrayList<String>(source.linesOutput);
		source.linesTemp.clear();
		return lastIndex = 0;
	}

	public List<String> createBlockCodeBeforeField(String sourceName, Field field) {
		List<String> code = new ArrayList<String>();
		if (!field.beforeFieldExists)
			code.add(field.afterFieldIdentation + "BEFORE FIELD " + field.name);
		code.add(field.afterFieldIdentation + "   CALL " + sourceName + "_save_cur_field_name('" + field.name + "')");
		code.add("");
		return code;
	}

	public List<String> createBlockCodeOnKey(String sourceName, WindowArea area) {
		List<String> code = new ArrayList<String>();
		code.add("");
		code.add(area.onKeyIdentation + "ON KEY (control-y)");
		if (area.fields.size() == 0) {
			code.add(area.onKeyIdentation + "   CALL " + sourceName + "_show_input_name('" + area.windowName + "')");
		} else {
			code.add(area.onKeyIdentation + "   CALL " + sourceName + "_show_field_name()");
		}
		code.add("");
		return code;
	}

	public List<String> createBlockCodeBeforeRow(String sourceName, WindowArea area) {
		List<String> code = new ArrayList<String>();
		if (!area.beforeRowExists)
			code.add(area.onKeyIdentation + "BEFORE ROW");
		code.add(area.onKeyIdentation + "   CALL " + sourceName + "_save_cur_field_name('" + area.windowName + "')");
		code.add("");
		return code;
	}

	public List<String> createBlockCodeBeforeInput(String sourceName, WindowArea area) {
		List<String> code = new ArrayList<String>();
		if (!area.beforeInputExists)
			code.add(area.onKeyIdentation + "BEFORE INPUT");
		code.add(area.onKeyIdentation + "   CALL " + sourceName + "_save_cur_field_name('" + area.windowName + "')");
		code.add("");
		return code;
	}

	public void createFunctionSaveFieldName() {
		List<String> code = new ArrayList<>();
		code.add("");
		code.add("#-------------------------#");
		code.add(" FUNCTION " + source.name + "_save_cur_field_name(l_field_name)");
		code.add("#-------------------------#");
		code.add("  DEFINE l_field_name  VARCHAR(50)");
		code.add("");
		code.add("  IF LOG_qaModeEnabled() THEN");
		code.add("    CALL AUTO_saveCurrentFieldName(l_field_name)");
		code.add("  END IF");
		code.add("END FUNCTION");
		code.add("");
		source.linesOutput.addAll(code);
	}

	public void createFunctionShowFieldName() {
		List<String> code = new ArrayList<>();
		code.add("");
		code.add("#-------------------------#");
		code.add(" FUNCTION " + source.name + "_show_field_name()");
		code.add("#-------------------------#");
		code.add("  IF LOG_qaModeEnabled() THEN");
		code.add("    CALL wms6085_help('AUTOMATION',''," + source.name + "_get_field_name())");
		code.add("  END IF");
		code.add("END FUNCTION");
		code.add("");
		source.linesOutput.addAll(code);
	}

	public void createFunctionShowInputName() {
		List<String> code = new ArrayList<>();
		code.add("");
		code.add("#-------------------------#");
		code.add(" FUNCTION " + source.name + "_show_input_name(l_input_name)");
		code.add("#-------------------------#");
		code.add("  DEFINE l_input_name  VARCHAR(50)");
		code.add("");
		code.add("  IF LOG_qaModeEnabled() THEN");
		code.add("    CALL wms6085_help('AUTOMATION','',l_input_name)");
		code.add("  END IF");
		code.add("END FUNCTION");
		code.add("");
		source.linesOutput.addAll(code);
	}

	public void createFunctionGetFieldName() {
		List<String> code = new ArrayList<>();
		Set<String> codeLineNotRepeat = new HashSet<String>();
		int maxLengthField = 0;
		code.add("");
		code.add("#-------------------------#");
		code.add(" FUNCTION " + source.name + "_get_field_name()");
		code.add("#-------------------------#");
		code.add("  CASE");
		for (WindowArea area : source.inputs)
			for (Field field : area.fields)
				if (maxLengthField < field.name.length())
					maxLengthField = field.name.length();

		for (WindowArea area : source.inputs) {
			for (Field field : area.fields) {
				codeLineNotRepeat.add("    WHEN infield(" + field.name + ") "
						+ getBlankSpace(maxLengthField - field.name.length()) + " RETURN '" + field.name + "'");
			}
		}
		code.addAll(codeLineNotRepeat);
		code.add("  END CASE");
		code.add("END FUNCTION");
		code.add("");
		source.linesOutput.addAll(code);
	}

	public void write(String name) throws IOException {

		File file = new File(file4GL.getParent() + "\\" + name + ".4gl");
		if (file.exists())
			file.delete();
		file.createNewFile();

		FileWriter fOut = new FileWriter(file);

		for (String s : source.linesOutput) {
			// System.out.println(s);
			fOut.write(s + System.lineSeparator());
		}
		fOut.flush();
		fOut.close();

		for (String s : errors) {
			 PrepareSource4GL.errorsList.add(name.replace("_temp", "") + " -> " + s);
		}

	}
	
}

class Field {

	String name;
	boolean beforeFieldExists;
	int lineNumberAfterField;
	String afterFieldIdentation;
}

class WindowArea {

	final static int WINDOW_TYPE_INPUT_ARRAY_OR_DISPLAY_ARRAY = 1;
	final static int WINDOW_TYPE_INPUT_OR_INPUT_BY_NAME = 2;

	String windowName;
	String onKeyIdentation;
	int lineFirstOnKey;
	int lineAfterRow;
	int windowType;
	boolean beforeRowExists;
	boolean beforeInputExists;

	List<Field> fields = new ArrayList<Field>();

	public WindowArea(final String windowName, final int windowType) {
		this.windowName = windowName;
		this.windowType = windowType;
	}

	public Field lastField() {
		return fields.get(fields.size() - 1);
	}
}

class Source {

	String name;
	List<String> lines = new ArrayList<String>();
	List<String> linesTemp = new ArrayList<String>();
	List<String> linesOutput = new ArrayList<String>();
	List<WindowArea> inputs = new ArrayList<WindowArea>();

	String line = null;
	String lineContentOriginal = null;

	public WindowArea lastInput() {
		return inputs.get(inputs.size() - 1);
	}
}
