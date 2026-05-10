# Como ejecutar

- Compilar:

```bash
javac -d out $(find src -name "*.java")
```

# Ejecutar

(En orden y terminales separadas)

```bash
java -cp out servidor.ServidorPrincipal
```

```bash
java -cp out servidor.ServidorTienda
```

```bash
java -cp out servidor.ServidorMatchmaking
```

```bash
java -cp out cliente.Cliente
```

---

Tambien ejecutando ./levantar_servidores.sh se levantan los 3 al mismo tiempo
