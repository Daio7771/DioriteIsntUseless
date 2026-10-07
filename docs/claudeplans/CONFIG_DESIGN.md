# Diorite Isn't Useless — Diseño: Archivo de configuración (v1.0)

> Ampliación de `DESIGN.md`. Para Claude Code.
> El director del proyecto es Daio: ante cualquier ambigüedad o decisión no cubierta aquí, **pregunta antes de implementar**.
> Prioridades: robustez y rendimiento por encima de la brevedad. El juego **nunca** debe crashear por culpa de la configuración.

---

## 1. Resumen de decisiones

| Tema | Decisión |
|---|---|
| Formato | **JSON** |
| Ubicación | `config/<modid>.json` (usar la ruta de configuración de Fabric Loader, no una ruta fija) |
| Pantalla en el juego | **Cloth Config**, accesible desde **Mod Menu** |
| Recarga sin reiniciar | Comando **`/diu reload`** |
| Servidor ↔ cliente | El servidor **sincroniza** sus valores con los clientes |
| Recetas | **Fuera** de la configuración (se cambian con datapacks) |

**Cloth Config y Mod Menu: dependencias OPCIONALES** (`suggests` en `fabric.mod.json`, no `depends`).
El mod debe funcionar perfectamente sin ellas; si están instaladas, aparece la pantalla de configuración. Sin ellas, se edita el JSON a mano.
> Decisión tomada en el diseño para no obligar a los jugadores a instalar más mods. Si Daio prefiere que sea obligatoria, cambiarlo.

---

## 2. Opciones

| Clave JSON | Tipo | Por defecto | Rango / valores | Se aplica | Sincronizada al cliente |
|---|---|---|---|---|---|
| `diorite.enabled` | bool | `true` | — | En caliente | **Sí** |
| `diorite.hardness` | float | `2.0` | 1.5 – 50.0 | En caliente | **Sí** |
| `diorite.blastResistance` | float | `12.0` | 6.0 – 1200.0 | En caliente | **Sí** |
| `treeFelling.enabled` | bool | `true` | — | En caliente | No |
| `treeFelling.maxLogs` | int | `128` | 1 – 512 | En caliente | No |
| `treeFelling.logsPerDurabilityPoint` | int | `2` | 1 – 10 | En caliente | No |
| `treeFelling.dropsAtOrigin` | bool | `true` | — | En caliente | No |
| `treeFelling.sneakMode` | string | `"SNEAK_DISABLES"` | `"SNEAK_DISABLES"` / `"SNEAK_ENABLES"` | En caliente | No |
| `axe.durability` | int | `1000` | 1 – 10000 | **Requiere reinicio** | No |
| `axe.restrictEnchantments` | bool | `true` | — | En caliente | No |

Notas:
- `diorite.enabled = false` → la diorita vuelve a los valores **vanilla** (1.5 / 6.0) y se ignoran `hardness` y `blastResistance`.
- `logsPerDurabilityPoint` sustituye al "0.5 por tronco" del diseño original: `2` equivale exactamente al comportamiento actual. Usar enteros mantiene el contador exacto y determinista.
- `sneakMode = "SNEAK_ENABLES"` invierte el control: el hacha tala solo un tronco salvo que el jugador esté agachado.
- `axe.durability` solo puede aplicarse al arrancar porque la durabilidad máxima se fija al registrar el ítem. Las hachas existentes conservan su desgaste.
- `axe.restrictEnchantments = false` → el hacha acepta los encantamientos normales de un hacha vanilla.

### Diorita "en caliente": requisito técnico
El mixin de la diorita **no** debe fijar los valores al arrancar: debe leerlos en cada consulta desde un valor en memoria (campo `@Volatile` o equivalente), para que `/diu reload` y la sincronización funcionen sin reiniciar.
Ese método se llama muchísimo: **nunca** leer el archivo ni hacer trabajo pesado dentro del mixin, solo leer el valor cacheado.

---

## 3. Formato del archivo

```json
{
  "configVersion": 1,
  "diorite": {
    "enabled": true,
    "hardness": 2.0,
    "blastResistance": 12.0
  },
  "treeFelling": {
    "enabled": true,
    "maxLogs": 128,
    "logsPerDurabilityPoint": 2,
    "dropsAtOrigin": true,
    "sneakMode": "SNEAK_DISABLES"
  },
  "axe": {
    "durability": 1000,
    "restrictEnchantments": true
  }
}
```

`configVersion` permite migrar el archivo en futuras versiones del mod sin perder los ajustes del jugador.

---

## 4. Robustez (obligatorio)

| Situación | Comportamiento |
|---|---|
| El archivo no existe | Crearlo con los valores por defecto |
| Falta una clave | Usar el valor por defecto **y reescribir el archivo** añadiendo la clave, sin tocar las demás |
| Valor fuera de rango | Ajustarlo al límite más cercano (clamp) + aviso en el log indicando clave, valor recibido y valor aplicado |
| Tipo incorrecto (ej. texto donde va un número) | Usar el valor por defecto de esa clave + aviso en el log |
| `sneakMode` desconocido | Usar `"SNEAK_DISABLES"` + aviso |
| JSON corrupto (no se puede leer) | **No crashear.** Renombrar el archivo a `<modid>.json.broken-<fecha>`, crear uno nuevo con valores por defecto y registrar un error claro en el log |
| Claves desconocidas | Ignorarlas sin borrarlas del archivo |

Escritura del archivo: escribir primero en un archivo temporal y luego reemplazar, para no dejar un JSON a medias si el juego se cierra en mitad de la escritura.

---

## 5. Comando `/diu reload`

- Solo en el servidor. Permiso de **operador (nivel 2)**.
- Vuelve a leer el archivo, lo valida (apartado 4) y aplica las opciones "en caliente".
- Después, **reenvía los valores sincronizados a todos los clientes conectados**.
- Mensaje de respuesta al que lo ejecuta:
  - Éxito: confirmación de recarga.
  - Si cambió una opción que requiere reinicio (`axe.durability`): avisar de que ese cambio se aplicará al reiniciar.
  - Si hubo valores corregidos: indicar cuántos, y que los detalles están en el log.
- Textos traducibles con claves en `en_us.json` (no texto fijo en el código).

---

## 6. Sincronización servidor → cliente

**Por qué:** la dureza la usa también el cliente para calcular cuánto tarda en romperse un bloque. Si cliente y servidor no coinciden, el bloque "se rompe" en pantalla y luego reaparece.

**Qué se sincroniza:** solo `diorite.enabled`, `diorite.hardness` y `diorite.blastResistance`.

**Cuándo:**
1. Cuando un jugador entra en el servidor.
2. Después de cada `/diu reload`, a todos los clientes conectados.

**Comportamiento del cliente:**
- Mientras está conectado a un servidor, usa **los valores del servidor**, ignorando su configuración local para esas 3 opciones.
- Al desconectarse, vuelve a su configuración local.
- En un solo jugador, el servidor integrado usa la configuración local y todo coincide.

**Paquete:** usar la API de red de Fabric. Incluir un número de versión del paquete para poder ampliarlo en el futuro sin romper la compatibilidad.

**En la pantalla de Cloth Config:** si el jugador está conectado a un servidor, indicar en las opciones de la diorita que se están usando los valores del servidor.

---

## 7. Pantalla de configuración (Cloth Config)

- Tres categorías: **Diorite**, **Tree Felling**, **Axe**.
- Cada opción con su rango (deslizador o campo numérico con límites) y un **tooltip** que explique qué hace.
- `axe.durability` marcada claramente como **"Requires restart"**.
- Al guardar: escribir el JSON y aplicar los cambios en caliente.
- Todos los textos en `en_us.json`.

---

## 8. Orden de implementación sugerido

1. Clase de configuración + lectura/escritura del JSON + robustez completa (apartado 4).
2. Conectar las opciones existentes al código (talado, hacha, diorita) sustituyendo los valores fijos.
3. `/diu reload`.
4. Sincronización servidor → cliente.
5. Pantalla de Cloth Config + integración con Mod Menu (opcionales).

Probar en el juego al terminar cada paso.

---

## 9. Pruebas de aceptación

- [ ] Sin archivo: se crea con los valores por defecto y el mod funciona igual que antes.
- [ ] `maxLogs = 10` + `/diu reload` → un árbol grande solo pierde 10 troncos, sin reiniciar.
- [ ] `logsPerDurabilityPoint = 1` → talar 20 troncos gasta 20 de durabilidad.
- [ ] `sneakMode = "SNEAK_ENABLES"` → sin agacharse corta un tronco; agachado tala el árbol.
- [ ] `treeFelling.enabled = false` → el hacha se comporta como un hacha normal.
- [ ] `diorite.enabled = false` → la diorita se mina y resiste explosiones como en vanilla.
- [ ] `diorite.hardness = 30` en el servidor y `2.0` en el cliente → al conectarse, minar diorita tarda lo del servidor y **no hay bloques que reaparecen**.
- [ ] Cambiar la dureza en el servidor + `/diu reload` → los clientes conectados notan el cambio sin reconectar.
- [ ] `axe.durability = 50` + `/diu reload` → el mensaje avisa de que requiere reinicio; tras reiniciar, el hacha tiene 50.
- [ ] `maxLogs = 99999` → se ajusta a 512 con aviso en el log.
- [ ] JSON roto a propósito (borrar una llave) → el juego arranca, se crea el `.broken-…` y un archivo nuevo.
- [ ] Sin Cloth Config ni Mod Menu → el mod funciona y el JSON se puede editar a mano.
- [ ] Con Cloth Config + Mod Menu → la pantalla aparece, guarda y aplica los cambios.
- [ ] Un jugador sin permisos de operador no puede usar `/diu reload`.
