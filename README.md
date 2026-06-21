# Como ejecutar

- Compilar:

```bash
javac -d out $(find src -name "*.java")
```

# Cluster

El sistema es un cluster P2P de nodos idénticos: cada `Nodo` sirve login +
Tienda + Matchmaking a los clientes (puerto cliente) y se coordina con los
demás nodos (puerto peer) usando relojes de Lamport, exclusión mutua
Ricart-Agrawala (sobre el stock de un ítem de oferta flash) y elección de
coordinador Bully, con heartbeats para detectar caídas.

La membresía se define en `config/peers.conf`:

```
# id,host,puertoCliente,puertoPeer
1,127.0.0.1,7001,8001
2,127.0.0.1,7002,8002
3,127.0.0.1,7003,8003
```

Levantar todo el cluster (compila y arranca un proceso por línea de
`config/peers.conf`):

```bash
./levantar_cluster.sh
```

O un nodo a la vez (en terminales separadas):

```bash
java -cp out cluster.Nodo 1 config/peers.conf
java -cp out cluster.Nodo 2 config/peers.conf
java -cp out cluster.Nodo 3 config/peers.conf
```

Cada nodo escribe su log en `logs/node<id>.log` y su PID en `logs/node<id>.pid`.

Conectar un cliente a un nodo específico:

```bash
java -cp out cliente.Cliente <host> <puertoCliente>
# ej: java -cp out cliente.Cliente localhost 7001
```

(`host`/`puerto` son opcionales; por defecto `localhost:7001`).

# Prueba de carga y falla inducida

```bash
java -cp out carga.GeneradorCarga [numHilos] [duracionSeg] [peersConfigPath]
# por defecto: 50 hilos, 60s, config/peers.conf
```

Reporta throughput, latencia promedio/p95, tasa de error y mensajes de
coordinación por consola, y exporta `out/carga_resultados.csv`.

Para inducir la caída del coordinador en plena corrida:

```bash
./scripts/matar_coordinador.sh
```

# Logs

Para fusionar y ordenar por marca de Lamport los logs de todos los nodos
(evidencia de orden causal entre procesos):

```bash
./scripts/merge_logs.sh
# escribe logs/merged.log
```
