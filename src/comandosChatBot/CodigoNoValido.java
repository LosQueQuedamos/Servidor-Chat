package comandosChatBot;

import java.io.IOException;

import paqueteEnvios.Comando;
import paqueteEnvios.PaqueteMensaje;
import servidor.EscuchaCliente;
import servidor.Servidor;

public class CodigoNoValido extends ComandoChatBot {
	private String msjFinal;
	@Override
	public void ejecutar() {
		msjFinal = "No se detectó ningun comando valido al que responder.";
		try {
			PaqueteMensaje paqueteMensaje = new PaqueteMensaje("Alfred", null, msjFinal, nombreSala);
			paqueteMensaje.setComando(Comando.CHATSALA);
			if (!nombreSala.equals("Ventana Principal")) {
				for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
					if (Servidor.getSalas().get(paqueteMensaje.getNombreSala()).getUsuariosConectados()
							.contains(conectado.getPaqueteUsuario().getUsername())) {
						conectado.getSalida().writeObject(gson.toJson(paqueteMensaje));
					}
				}
				String msjAgregar = paqueteMensaje.getUserEmisor() + ": " + paqueteMensaje.getMsjChat() + "\n";
				Servidor.getSalas().get(paqueteMensaje.getNombreSala()).agregarMsj(msjAgregar);
				Servidor.getConector().guardarChatSala(paqueteMensaje);
			} else {
				paqueteMensaje.setComando(Comando.CHATALL);
				for (EscuchaCliente conectado : Servidor.getClientesConectados()) {
					conectado.getSalida().writeObject(gson.toJson(paqueteMensaje));
				}
			}
		} catch (IOException e) {
			Servidor.getLog().append("Error al tratar de informar que no hubo un comando valido"+ System.lineSeparator());
			e.printStackTrace();
		}		
	}

}
