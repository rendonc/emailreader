package com.emailreader.app;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Date;
import java.util.Properties;

import javax.mail.Flags.Flag;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;

import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.AndTerm;
import javax.mail.search.BodyTerm;
import javax.mail.search.SearchTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author CRC
 *Clase que se encapsula la logica de conexion a la cuenta de correo Gmail y la lectura de mensajes
 */
public class IMAPEmailInbox {

	private static Logger log = LoggerFactory.getLogger(IMAPEmailInbox.class);

	private MySQLStorage storage;

	private String host;

	private String password;

	private SearchTerm searchTerm;

	private Store store;

	private String username;

	/**
	 * Constructor que recibe los parametros de inicializacion para realizar conexion al servidor de correo electronico
	 * @param host Nombre o IP del servidor IMAP de correo electronico
	 * @param port Puerto de conexion IMAP al servidor de correo electronico
	 * @param username Cuenta de correo electronico de donde se leeran los correos que contengan la palabra clave
	 * @param password Password de acceso a la cuenta de correo electronico 
	 * @param keyword Palabra clave que se buscara dentro del body de los mensajes no leidos para su almacenamiento
	 * @throws NoSuchProviderException
	 */
	public IMAPEmailInbox(String host, String port, String username, String password, String keyword)
			throws NoSuchProviderException {
		Properties props = new Properties();
		props.put("mail.imap.host", host);
		props.put("mail.imap.socketFactory.port", port);
		props.put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		props.put("mail.imap.auth", "true");
		props.put("mail.imap.port", port);
		Session session = Session.getDefaultInstance(props, null);

		/*
		 * se especifica 'imaps' para emplear la version
		 * segura de IMAP 
		 */
		store = session.getStore("imaps");

		this.host = host;
		this.username = username;
		this.password = password;

		searchTerm = getSearchTerm(keyword);

	}

	/**
	 * Metodo que descarga los mensajes de la cuenta de correos electronico que cumplen con el criterio
	 * de busqueda para los almacenarlos en el medio de persistencia por medio del objeto de tipo MySQLStorage  
	 * @throws EmailConnectionException
	 */
	public void downloadEmails() throws EmailConnectionException {

		log.debug("trying to connect to email store...");

		try {
			store.connect(host, username, password);
		} catch (MessagingException e) {
			log.error("Error while connecting to store, verify host, user name, and password", e);
			throw new EmailConnectionException();
		}

		Folder inbox = null;
		try {
			inbox = store.getFolder("inbox");
			inbox.open(Folder.READ_WRITE);
			int messageCount = inbox.getMessageCount();
			log.debug("Total Email Messages in Inbox:" + messageCount);
		} catch (MessagingException e) {
			log.error("Error while getting store 'inbox'", e);
			try {
				store.close();
			} catch (MessagingException e1) {
				log.error("Error while closing folder 'inbox'", e1);
			}
			return;
		}

		Message[] messages;
		try {
			messages = inbox.search(searchTerm);
			log.info("Total unseen messages with keyword: {}",messages.length);
			for (Message msg : messages) {
				String from = "unknown";
				if (msg.getReplyTo().length >= 1) {
					from = msg.getReplyTo()[0].toString();
				} else if (msg.getFrom().length >= 1) {
					from = msg.getFrom()[0].toString();
				}
				
				from=getEmailAddress(from);

				String subject = msg.getSubject();
				Date date = msg.getReceivedDate();

				try {
					storage.saveMessage(date, from, subject);
					msg.setFlag(Flags.Flag.SEEN, true);
				} catch (SQLIntegrityConstraintViolationException e) {
					log.error(
							"Error while storing message, duplicate entry [" + date + "," + from + "," + subject + "]");
				} catch (SQLException e) {
					log.error("Error while storing message [" + date + "," + from + "," + subject + "]", e);
				}

			}
		} catch (MessagingException e) {
			log.error("Error while searching for messages in store 'inbox'", e);
		} finally {
			try {
				inbox.close(true);
			} catch (MessagingException e) {
				log.error("Error while closing folder 'inbox'", e);
			}
			try {
				store.close();
			} catch (MessagingException e) {
				log.error("Error while closing folder 'inbox'", e);
			}
		}
	}

	/**
	 * Metodo para extraer la direccion de correo electronico que se encuentra dentro los caracteres '<' '>'
	 * @param from String con una direccion de correo electronico
	 * @return String que contiene la direccion de correo electronico
	 */
	private String getEmailAddress(String from) {
		int pos1=from.indexOf('<');
		int pos2=from.indexOf('>');
		/*
		 * la direccion de correo electronico no se encuentra con formato <direccion@correo.com>
		 */
		if(pos1<0 || pos2<0 || pos2<pos1) {
			return from;
		}
		return from.substring(pos1+1, pos2);
	}

	
	/**
	 * Metodo que construye un objetivo de tipo SearchTerm para filtrar los mensajes que no han sido 
	 * marcados como leidos y que contienen la palabra clave en el cuerpo del mensaje
	 * @param keyword Palabra clave que se desea encontrar en el cuerpo del mensaje
	 * @return Objeto de tipo SearchTerm que contiene el filtro para la busqueda
	 */
	private SearchTerm getSearchTerm(String keyword) {
		@SuppressWarnings("serial")
		SearchTerm notSeen = new SearchTerm() {

			public boolean match(Message message) {
				try {

					if (!message.isSet(Flag.SEEN)) {
						return true;
					}
				} catch (MessagingException e) {
					log.error("Error while matching message with Flag.SEEN", e);
				}
				return false;
			}
		};

		/*
		 * Filtro para buscar mensajer no leidos y con keyword en el cuerpo del mensaje
		 */
		SearchTerm searchTerm = new AndTerm(notSeen, new BodyTerm(keyword));
		return searchTerm;
	}

	/**
	 * Metodo setter para el objeto de tipo MySQLStorage que se encarga de persistir
	 * los mensajes que cumplan con los filtros de busqueda
	 * @param db
	 */
	public void setStorage(MySQLStorage db) {
		this.storage = db;
	}

}