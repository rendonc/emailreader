package com.emailreader.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author CRC
 *Clase que se encapsula la logica de conexion a la base de datos MySQL
 */
public class MySQLStorage {

	private static Logger log = LoggerFactory.getLogger(MySQLStorage.class);

	private String user;
	private int port;
	private String password;
	private String host;
	private String dbName;
	private Connection connection;

	private String SQL_INSERT;

	private String driverString;

	/**
	 * Constructor que recibe los parametros de inicializacion para realizar conexion al servidor MySQL
	 * @param db_host Nombre o IP del host donde se encuentra la base de datos MySQL
	 * @param db_port Puerto de escucha del servidor MySQL
	 * @param db_name Nombre de la base de datos donde se almacenaran los mensajes
	 * @param db_user Login de usuario 
	 * @param db_password Password de usuario
	 */
	public MySQLStorage(String db_host, int db_port, String db_name, String db_user, String db_password) {
		this.user = db_user;
		this.port = db_port;
		this.password = db_password;
		this.host = db_host;
		this.dbName = db_name;
		this.SQL_INSERT = "INSERT INTO " + dbName + ".correo(fecha,remitente,asunto) VALUES(?,?,?)";

		StringBuffer sb = new StringBuffer();
		sb.append("jdbc:mysql://");
		sb.append(host);
		sb.append(":");
		sb.append(port);
		sb.append("/");
		//se agrega para mayor compatibilidad con la zona horaria del servidor MySQL
		sb.append("?serverTimezone=UTC#");
		sb.append("/");
		sb.append(dbName);
		driverString = sb.toString();
	}

	/**
	 * Metodo que se encarga de realizar la conexion al servidor MySQL
	 * @throws SQLException
	 */
	public void connect() throws SQLException {
		connection = DriverManager.getConnection(driverString, user, password);
	}

	/**
	 * Metodo que se encarga de cerrar la conexion al servidor MySQL
	 * @throws SQLException
	 */
	public void disconnect() throws SQLException {
		connection.close();
	}

	/**
	 *Metodo que se encarga de almacenar los datos del email
	 * @param date Fecha de recepcion del email
	 * @param from Remitente del email
	 * @param subject Asunto del email
	 * @throws SQLException
	 */
	public void saveMessage(Date date, String from, String subject) throws SQLException {

		log.info("Inserting [" + date + ", " + from + ", " + subject + "]");

		PreparedStatement pstmt = connection.prepareStatement(SQL_INSERT);
		pstmt.setTimestamp(1, new java.sql.Timestamp(date.getTime()));
		pstmt.setString(2, from);
		pstmt.setString(3, subject);
		pstmt.execute();
		pstmt.close();
	}

}
