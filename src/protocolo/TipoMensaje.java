package protocolo;

public enum TipoMensaje {
    PING,
    PONG,
    JOIN,
    SYNC_RESPONSE,
    ELECTION,
    OK,
    COORDINATOR,
    RA_REQUEST,
    RA_REPLY,
    TX_COMMIT,
    LOBBY_CREADO,
    LOBBY_CERRADO
}
