package com.emailreader.app;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import javax.mail.NoSuchProviderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author CRC Clase principaldel programa que automatiza la conexion a una
 *         cuenta Gmail para buscar los mensajes que en el cuerpo contengan una
 *         palabra predefinida, si el mensaje posee la palabra buscada el
 *         programa almacena, en una base de datos MySQL dentro de una tabla
 *         llamada 'correo', la fecha, remitente y asunto del mensaje.
 */
public class EmailReaderApp {

	/*
	 * Archivo de configuracion con los parametros de conexion a la cuenta de correo
	 * Gmail y la base de datos MySQL.
	 */
	private static final String propertiesFile = "app.properties";

	static {
		/*
		 * Se configura Log4J por medio del archivo "config/log4j2.xml"
		 */
		System.setProperty("log4j.configurationFile", "log4j2.xml");
	}

	private static Logger log = LoggerFactory.getLogger(EmailReaderApp.class);

	private MySQLStorage storage;

	private IMAPEmailInbox inbox;

	/*
	 * Periodo en segundos para consultar la bandeja de entrada de la cuenta
	 * de correo electronico  
	 */
	private int POLLING_SECONDS;

	public static void main(String[] args) {

		Properties props = readPropertiesFile(propertiesFile);

		EmailReaderApp app = new EmailReaderApp(props);

		app.start();
	}

	/**
	 * Constructor de clase que inicializa los componentes de la aplicacion
	 * 
	 * @param props Objetivo properties que contiene los parametros de inicializacion
	 * de la conexion IMAP y la conexion a la base de datos
	 */
	public EmailReaderApp(Properties props) {

		try {
			POLLING_SECONDS = Integer.parseInt(props.getProperty("polling.interval"));
		} catch (Exception e) {
			log.error("Error while parsing db port" + props.getProperty("db.port"), e);
			System.exit(1);
		}

		this.storage = initializeStorage(props);

		try {
			this.inbox = initializeInbox(props);
		} catch (NoSuchProviderException e) {
			log.error("Error while getting email store with provider 'imaps'", e);
			System.exit(1);
		}

		this.inbox.setStorage(storage);
	}

	/**
	 * Metodo que se encarga de iniciar el polling a la cuenta de correo para buscar
	 * mensajes no leidos y almacenar en la base de datos aquellos mensajes que cumplan
	 * con el filtro de busqueda
	 */
	private void start() {
		while (true) {
			try {
				storage.connect();
			} catch (SQLException e) {
				log.error("Error while connecting to database, verify database parameters", e);
				System.exit(1);

			}
			try {
				inbox.downloadEmails();
			} catch (EmailConnectionException e) {
				log.error("Error while connecting to email account, verify email parameters");
				System.exit(1);
			}

			try {
				storage.disconnect();
			} catch (SQLException e) {
				log.error("Error while disconnecting from from database", e);
			}

			try {
				log.debug("Thread sleeping {} second...", POLLING_SECONDS);
				Thread.sleep(POLLING_SECONDS * 1000);
			} catch (InterruptedException e) {
				log.error("Error while sleeping Thread", e);
			}

		}

	}

	/**
	 * Metodo que construye un objeto de tipo MySQLStorage con los parametros
	 * especificados en el objeto de tipo Properties. 
	 * @param props Objeto Properties que contiene los datos de inicializacion para
	 *              la conexion con el servidor MySQL. Los parametros buscados son:
	 * 				db.name, db.username, db.password, db.host
	 * @return Objeto que encapsula la logica de conexion a la base de datos
	 */
	private MySQLStorage initializeStorage(Properties props) {
		String db_name = props.getProperty("db.name");
		String db_user = props.getProperty("db.username");
		String db_password = props.getProperty("db.password");
		String db_host = props.getProperty("db.host");

		int db_port = 0;
		try {
			db_port = Integer.parseInt(props.getProperty("db.port"));
		} catch (Exception e) {
			log.error("Error while parsing db port" + props.getProperty("db.port"), e);
			System.exit(1);
		}

		MySQLStorage storage = new MySQLStorage(db_host, db_port, db_name, db_user, db_password);
		return storage;
	}

	/**
	 * Metodo que construye un objeto de tipo IMAPEmailInbox con los parametros
	 * especificados en el objeto de tipo Properties. 
	 * @param props Objeto Properties que contiene los datos de inicializacion para
	 *              la conexion con el servidor de correo IMAP. Los parametros buscados son:
	 * 				email.username, email.password, email.keyword, email.host, email.port
	 * @return Objeto que encapsula la logica de conexion al inbox del servidor de email
	 * @throws NoSuchProviderException
	 */
	private IMAPEmailInbox initializeInbox(Properties props) throws NoSuchProviderException {
		String username = props.getProperty("email.username");
		String password = props.getProperty("email.password");
		String keyword = props.getProperty("email.keyword");
		String host = props.getProperty("email.host");
		String port = props.getProperty("email.port");

		IMAPEmailInbox inbox = null;

		inbox = new IMAPEmailInbox(host, port, username, password, keyword);

		return inbox;
	}

	/**
	 * Metodo que obtiene los parametros de configuracion del programa
	 * mediante la lectura de un archivo properties
	 * 
	 * @param fileName Nombre del archivo properties que contiene los parametros de ejecucion
	 * @return Objeto Properties que contiene los parametros de ejecucion
	 */
	public static Properties readPropertiesFile(String fileName) {
		FileInputStream fis = null;
		Properties prop = null;
		try {
			fis = new FileInputStream(fileName);
			prop = new Properties();
			prop.load(fis);
		} catch (FileNotFoundException e) {
			log.error("Properties file " + fileName + " not found", e);
			System.exit(1);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		} finally {
			try {
				fis.close();
			} catch (IOException e) {
				log.error("Error while closing file", e);
			}
		}
		return prop;
	}

}
