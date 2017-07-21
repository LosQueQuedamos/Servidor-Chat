package servidor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import com.google.gson.Gson;

import paqueteEnvios.Comando;
import paqueteEnvios.Paquete;
import paqueteEnvios.PaqueteDeUsuariosYSalas;
import paqueteEnvios.PaqueteMensaje;
import paqueteEnvios.PaqueteSala;
import paqueteEnvios.PaqueteUsuario;

public class EscuchaCliente extends Thread {

	private final Socket socket;
	private final ObjectInputStream entrada;
	private final ObjectOutputStream salida;

	private final Gson gson = new Gson();

	private PaqueteUsuario paqueteUsuario;
	private PaqueteDeUsuariosYSalas paqueteDeUsuarios;
	private PaqueteMensaje paqueteMensaje;

	public EscuchaCliente(String ip, Socket socket, ObjectInputStream entrada, ObjectOutputStream salida) {
		this.socket = socket;
		this.entrada = entrada;
		this.salida = salida;
		paqueteUsuario = new PaqueteUsuario();
	}

	public void run() {
		try {
			Paquete paquete;
			Paquete paqueteSv = new Paquete(null, 0);
			PaqueteUsuario paqueteUsuario = new PaqueteUsuario();
			PaqueteSala paqueteSala = new PaqueteSala();

			String cadenaLeida = (String) entrada.readObject();

			while (!((paquete = gson.fromJson(cadenaLeida, Paquete.class)).getComando() == Comando.DESCONECTAR)) {							

				switch (paquete.getComando()) {

				case Comando.INICIOSESION:
					paqueteSv.setComando(Comando.INICIOSESION);

					// Recibo el paquete usuario
					paqueteUsuario = (PaqueteUsuario) (gson.fromJson(cadenaLeida, PaqueteUsuario.class));

					// Si se puede loguear el usuario le envio un mensaje de exito y el paquete usuario con los datos
					if (Servidor.getConector().loguearUsuario(paqueteUsuario)) {

						PaqueteDeUsuariosYSalas pus = new PaqueteDeUsuariosYSalas(Servidor.getUsuariosConectados(), Servidor.getNombresSalasDisponibles());
						pus.setComando(Comando.INICIOSESION);
						pus.setMensaje(Paquete.msjExito);

						Servidor.UsuariosConectados.add(paqueteUsuario.getUsername());

						// Consigo el socket, y entonces ahora pongo el username y el socket en el map
						int index = Servidor.UsuariosConectados.indexOf(paqueteUsuario.getUsername());
						Servidor.mapConectados.put(paqueteUsuario.getUsername(), Servidor.SocketsConectados.get(index));

						salida.writeObject(gson.toJson(pus));

						// COMO SE CONECTO 1 LE DIGO AL SERVER QUE LE MANDE A TODOS LOS QUE SE CONECTAN
						synchronized(Servidor.atencionConexiones){
							Servidor.atencionConexiones.notify();
						}
						break;

					} else {
						paqueteSv.setMensaje(Paquete.msjFracaso);
						salida.writeObject(gson.toJson(paqueteSv));
						synchronized (this) {
							this.wait(200);
						}
						break;						
					}

				case Comando.TALK:
					paqueteMensaje = (PaqueteMensaje) (gson.fromJson(cadenaLeida, PaqueteMensaje.class));
					if (Servidor.mensajeAUsuario(paqueteMensaje)) {

						paqueteMensaje.setComando(Comando.TALK);

						Socket s1 = Servidor.mapConectados.get(paqueteMensaje.getUserReceptor());

						for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
							if(conectado.getSocket() == s1)	{
								conectado.getSalida().writeObject(gson.toJson(paqueteMensaje));	
							}
						}

					} else {
						System.out.println("Server: Mensaje No Enviado!");
					}
					break;

				case Comando.CHATALL:
					paqueteMensaje = (PaqueteMensaje) (gson.fromJson(cadenaLeida, PaqueteMensaje.class));
					paqueteMensaje.setComando(Comando.CHATALL);

					Socket s1 = Servidor.mapConectados.get(paqueteMensaje.getUserEmisor());
					int count = 0;
					for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
						if(conectado.getSocket() != s1)	{
							conectado.getSalida().writeObject(gson.toJson(paqueteMensaje));
							count++;
						}
					}
					Servidor.mensajeAAll(count);

					break;
				case Comando.MP:
					paqueteMensaje = (PaqueteMensaje) (gson.fromJson(cadenaLeida, PaqueteMensaje.class));
					if (Servidor.mensajeAUsuario(paqueteMensaje)) {

						paqueteMensaje.setComando(Comando.MP);

						Socket socketDestino = Servidor.mapConectados.get(paqueteMensaje.getUserReceptor());

						for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
							if(conectado.getSocket() == socketDestino)	{
								conectado.getSalida().writeObject(gson.toJson(paqueteMensaje));	
							}
						}

					} else {
						System.out.println("Server: Mensaje No Enviado!");
					}
					break;

				case Comando.NEWSALA:
					paqueteSala = (PaqueteSala) (gson.fromJson(cadenaLeida, PaqueteSala.class));
					if(Servidor.getConector().registrarSala(paqueteSala)){
						Servidor.getNombresSalasDisponibles().add(paqueteSala.getName());
						// COMO SE CREO 1 SALA NUEVA LE DIGO AL SERVER QUE LE MANDE A TODOS LOS QUE SE CONECTAN
						synchronized(Servidor.atencionNuevasSalas){
							Servidor.atencionNuevasSalas.notify();
						}
					} else {
						paqueteSala.setComando(Comando.NEWSALA);
						paqueteSala.setMensaje(Paquete.msjFracaso);
						salida.writeObject(gson.toJson(paqueteSala));
					}

					break;
				case Comando.REGISTRO:
					paqueteUsuario = (PaqueteUsuario) (gson.fromJson(cadenaLeida, PaqueteUsuario.class));
					if (Servidor.getConector().registrarUsuario(paqueteUsuario)) {

						paqueteUsuario.setComando(Comando.REGISTRO);
						paqueteUsuario.setMensaje(Paquete.msjExito);

						Servidor.UsuariosConectados.add(paqueteUsuario.getUsername());

						// Consigo el socket, y entonces ahora pongo el username y el socket en el map
						int index = Servidor.UsuariosConectados.indexOf(paqueteUsuario.getUsername());
						Servidor.mapConectados.put(paqueteUsuario.getUsername(), Servidor.SocketsConectados.get(index));

						salida.writeObject(gson.toJson(paqueteUsuario));

						// COMO SE CONECTO 1 LE DIGO AL SERVER QUE LE MANDE A TODOS LOS QUE SE CONECTAN
						synchronized(Servidor.atencionConexiones){
							Servidor.atencionConexiones.notify();
						}
						// Si el usuario no se pudo registrar le envio un msj de fracaso
					} else {
						paqueteUsuario.setMensaje(Paquete.msjFracaso);
						salida.writeObject(gson.toJson(paqueteUsuario));
					}
					break;
					
				case Comando.ENTRARSALA:
					paqueteSala = (PaqueteSala) (gson.fromJson(cadenaLeida, PaqueteSala.class));
					paqueteSala.setComando(Comando.ENTRARSALA);
					if(Servidor.getNombresSalasDisponibles().contains(paqueteSala.getName())) {
						Servidor.getSalas().get(paqueteSala.getName()).getUsuariosConectados().add(paqueteSala.getCliente());
						paqueteSala = Servidor.getSalas().get(paqueteSala.getName());
						paqueteSala.setMensaje(Paquete.msjExito);
						paqueteSala.setComando(Comando.ENTRARSALA);
						salida.writeObject(gson.toJson(paqueteSala));
					} else {
						paqueteSala.setMensaje(Paquete.msjFracaso);
						salida.writeObject(gson.toJson(paqueteSala));
					}
					break;
					
				default:
					break;
				}

				salida.flush();

				synchronized (entrada) {
					cadenaLeida = (String) entrada.readObject();
				}
			}
			Servidor.log.append(paqueteUsuario.getUsername() + " ha Cerrado Sesión" + System.lineSeparator());

			entrada.close();
			salida.close();
			socket.close();

			int index = Servidor.UsuariosConectados.indexOf(paqueteUsuario.getUsername());
			Servidor.SocketsConectados.remove(index);
			Servidor.getPersonajesConectados().remove(paqueteUsuario.getUsername());
			Servidor.getUsuariosConectados().remove(paqueteUsuario.getUsername());
			Servidor.getClientesConectados().remove(this);

			for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
				paqueteDeUsuarios = new PaqueteDeUsuariosYSalas(Servidor.getUsuariosConectados());
				paqueteDeUsuarios.setComando(Comando.CONEXION);
				conectado.salida.writeObject(gson.toJson(paqueteDeUsuarios, PaqueteDeUsuariosYSalas.class));
			}

			Servidor.log.append(paquete.getIp() + " cerró su Sesión " + System.lineSeparator());

		} catch (IOException | ClassNotFoundException e) {
			Servidor.log.append("Hubo un error de conexion: " + e.getMessage() + System.lineSeparator());
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} 
	}

	public Socket getSocket() {
		return socket;
	}

	public ObjectInputStream getEntrada() {
		return entrada;
	}

	public ObjectOutputStream getSalida() {
		return salida;
	}

	public PaqueteUsuario getPaqueteUsuario() {
		return paqueteUsuario;
	}

	public void setPaqueteUsuario(PaqueteUsuario paqueteUsuario) {
		this.paqueteUsuario = paqueteUsuario;
	}
}