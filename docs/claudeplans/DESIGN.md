# Diorite Isn't Useless — Documento de diseño (v1 / MVP)

> Documento para Claude Code. Describe **qué** debe hacer el mod y **cómo debe comportarse**.
> El director del proyecto es Daio: ante cualquier ambigüedad o decisión de diseño no cubierta aquí, **pregunta antes de implementar**.

---

## 0. Contexto técnico

| Campo | Valor |
|---|---|
| Minecraft | **1.20.1** |
| Loader | Fabric + Fabric API |
| Lenguaje | **Kotlin** (`fabric-language-kotlin`) |
| Java | 17 |
| Entorno | `"environment": "*"` (cliente + servidor) |
| Mod ID / paquete | **Leer de `fabric.mod.json` y de `src/`.** No inventar ni cambiar el mod ID. En este documento se usa `<modid>` como marcador. |

**Prioridades del código:** robustez y rendimiento por encima de la brevedad. Código claro, comentado donde la lógica no sea obvia, sin trucos frágiles.

**Regla de lados:** toda la lógica de juego (talado, durabilidad, encantamientos, dureza) se ejecuta en el **servidor lógico**. El cliente solo carga recursos (modelos, texturas, idioma).

---

## 1. Concepto

> *"La piedra que nadie quería es la única que aguanta."*

La diorita deja de ser inútil por dos vías:

```
⛏️ DIORITA (bloque vanilla modificado)
   Dureza 1.5 → 2.0  |  Resistencia a explosiones 6 → 12
        │
        ├── Vía vanilla: construir con ella (bases más resistentes)
        │
        └── Vía del mod:
              Horno / Alto horno:  1 Diorita        → 6 Diorite Crystal (+ XP)
              Mesa de trabajo:     6 Diorite Crystal → 1 Dioritine Ingot
              Mesa de trabajo:     3 Dioritine Ingot + 2 palos → 1 Dioritine Axe
```

---

## 2. Contenido

### 2.1 Modificación de la diorita vanilla

| Propiedad | Vanilla | Mod |
|---|---|---|
| Dureza | 1.5 | **2.0** |
| Resistencia a explosiones | 6.0 | **12.0** |

- **Solo** afecta a `minecraft:diorite`. **No** a diorita pulida, losas, escaleras ni muros.
- Herramienta necesaria y drops: sin cambios respecto a vanilla.
- Requiere **mixin**, porque las propiedades de bloques vanilla no se cambian por registro. Implementarlo de la forma menos invasiva posible (por ejemplo, interceptando el valor de dureza / resistencia solo cuando el bloque sea `Blocks.DIORITE`), sin romper otros bloques ni otros mods.

### 2.2 Ítems

| ID | Nombre (en_us) | Pestaña creativa | Stack |
|---|---|---|---|
| `<modid>:diorite_crystal` | Diorite Crystal | Ingredients | 64 |
| `<modid>:dioritine_ingot` | Dioritine Ingot | Ingredients | 64 |
| `<modid>:dioritine_axe` | Dioritine Axe | Tools & Utilities | 1 |

Sin pestaña creativa propia: se añaden a las pestañas vanilla con `ItemGroupEvents`.

### 2.3 Recetas

**Fundición (horno y alto horno):**

| Entrada | Salida | XP | Tiempo |
|---|---|---|---|
| 1 `minecraft:diorite` | **6** `diorite_crystal` | 0.35 | Horno 200 ticks / Alto horno 100 ticks |

> ⚠️ **Problema conocido:** en 1.20.1 las recetas de fundición JSON vanilla **no admiten `count` en el resultado** (siempre dan 1). Para conseguir 6 cristales hace falta una solución propia (por ejemplo, un serializador de receta de cocción personalizado que lea `count`, registrado para `smelting` y `blasting`). Elegir la opción más robusta y compatible, y **explicar a Daio la decisión** antes de implementarla.

**Lingote (con forma):**
```
C C C
C C C        C = diorite_crystal  →  1 dioritine_ingot
```
(Es normal que Minecraft permita colocar las 2 filas arriba o abajo.)

**Hacha (con forma, como el hacha vanilla; el espejo funciona automáticamente):**
```
I I
I S          I = dioritine_ingot, S = minecraft:stick  →  1 dioritine_axe
  S
```

Añadir también las entradas al **recipe book** de forma normal (las recetas JSON lo hacen solas).

---

## 3. Dioritine Axe — especificación detallada

### 3.1 Estadísticas

| Propiedad | Valor |
|---|---|
| Durabilidad | **1000** |
| Daño de ataque total | **0.5** (peor que el puño, que hace 1) |
| Velocidad de ataque | 1.0 |
| Velocidad de minado en madera | 6.0 (como el hierro) |
| Velocidad en bloques que no son madera | **0.5×** (más lento que a mano) |
| Reparación en yunque | Con `dioritine_ingot` |
| Encantabilidad | 14 |
| Quitar corteza (clic derecho en tronco) | Sí, comportamiento vanilla de hacha |

**Madera válida** = cualquier bloque en el tag `minecraft:logs` (incluye troncos del Overworld, madera, versiones sin corteza y los tallos carmesí y distorsionados del Nether).

### 3.2 Habilidad: talar el árbol entero

Al romper un bloque de `minecraft:logs` con el hacha:

1. **Si el jugador está agachado (Shift):** se rompe solo ese bloque, comportamiento normal.
2. **Si no:** se buscan los troncos conectados y se rompen todos.

**Búsqueda:**
- Algoritmo BFS (en anchura) desde el bloque roto.
- Vecindad de **26 bloques** (incluidas diagonales), para cubrir acacias, robles oscuros y árboles con ramas.
- Solo cuenta bloques del tag `minecraft:logs`.
- **Máximo 128 troncos en total**, contando el inicial. Al llegar al límite, se para.
- Nunca usar recursión (evitar desbordamiento de pila) y no volver a disparar la habilidad por los bloques que rompe ella misma.

**Drops:**
- Todos los drops de todos los troncos aparecen **juntos en la posición del bloque que golpeó el jugador**.
- Usar la tabla de botín real de cada bloque (con la herramienta como contexto), no drops inventados.

**Durabilidad (coste reducido):**
- Cada tronco roto cuesta **0.5** de durabilidad, incluido el primero (que en vanilla costaría 1).
- Implementación: contador fraccionario guardado en el NBT del ítem (por ejemplo `DioritineWear`). Cada 2 troncos = 1 punto de durabilidad real. Debe ser exacto y determinista, sin aleatoriedad.
- Si el hacha se rompe a mitad de una talada, **se detiene ahí**.
- En **modo creativo** no se consume durabilidad.

**Seguridad y compatibilidad:**
- Ejecutar solo en el servidor.
- Respetar permisos: no romper bloques que el jugador no podría romper normalmente (protección de spawn, mods de protección, modo aventura). Usar los mecanismos de Fabric/vanilla para comprobarlo.
- Disparar los eventos de rotura de bloque adecuados para que otros mods puedan reaccionar o cancelarlo.

### 3.3 Encantamientos

Solo se permiten **Efficiency** y **Mending**. Ningún otro, en ninguna vía:
- Mesa de encantar: solo puede ofrecer Efficiency.
- Yunque (libros o combinar hachas): solo acepta Efficiency y Mending.
- Comandos de admin (`/enchant`): pueden seguir el mismo filtro si es sencillo; si complica mucho, consultar a Daio.

Probablemente requiere un **segundo mixin**. Hacerlo de forma que solo afecte a `dioritine_axe`.

---

## 4. Recursos

### 4.1 Idioma
- **Solo inglés** (`assets/<modid>/lang/en_us.json`). No añadir otros idiomas.

### 4.2 Modelos
- `diorite_crystal` y `dioritine_ingot`: `item/generated`.
- `dioritine_axe`: `item/handheld` (para que se sujete como una herramienta).

### 4.3 Texturas provisionales (generadas por código)

3 PNG de **16×16**, fondo transparente, en `assets/<modid>/textures/item/`.

**Generarlas con un script de Python + Pillow** guardado en el repo (por ejemplo `tools/generate_textures.py`), para poder regenerarlas o ajustarlas. Las texturas definitivas las hará Daio más adelante y sustituirán a estas.

**No copiar ni recolorear texturas de Mojang.** Todo debe ser original.

**Paleta común:**

| Uso | Color |
|---|---|
| Diorita base | `#E3E3E3` |
| Sombra | `#A8A8A8` |
| Motas (manchas típicas de la diorita) | `#3A3A3A` |
| Brillo del cristal (tinte lila) | `#F5F0FF` |
| Contorno oscuro | elegir un gris oscuro coherente |
| Mango del hacha | marrones tipo madera, originales |

**Formas:**
- **Diorite Crystal:** esquirla o gema, blanca con brillo lila, pocas motas.
- **Dioritine Ingot:** forma de lingote, blanco grisáceo con motas negras (que se reconozca la diorita a primera vista).
- **Dioritine Axe:** mango de madera en diagonal, cabeza del color del lingote con motas.

Estilo pixel art limpio: píxeles exactos, sin suavizado ni degradados borrosos, contorno legible.

---

## 5. Orden de implementación sugerido

Hacerlo **por fases**, comprobando en el juego (`runClient`) al terminar cada una antes de seguir:

1. **Ítems básicos:** registrar `diorite_crystal` y `dioritine_ingot`, modelos, `en_us.json`, texturas provisionales, pestaña Ingredients.
2. **Recetas:** lingote (mesa) + fundición 1 → 6 con XP (horno y alto horno).
3. **Hacha sin habilidad:** material de herramienta, estadísticas, reparación, receta, pestaña Tools.
4. **Habilidad de talado:** BFS, límite 128, Shift, drops agrupados, durabilidad fraccionaria.
5. **Mixin 1:** dureza y resistencia de la diorita.
6. **Mixin 2:** filtro de encantamientos.

---

## 6. Pruebas de aceptación (comprobar en el juego)

- [ ] El mod aparece en la lista de mods y el juego arranca sin errores en el log.
- [ ] 1 diorita en horno → 6 cristales + XP. Igual en el alto horno, al doble de velocidad.
- [ ] 6 cristales → 1 lingote. 3 lingotes + 2 palos → hacha (en ambas orientaciones).
- [ ] Diorita: tarda algo más en minarse que la piedra; la diorita pulida sigue igual que en vanilla.
- [ ] Una explosión de creeper junto a una pared de diorita hace claramente menos daño que a una de piedra.
- [ ] Hacha sobre un roble: cae entero y los drops aparecen en el punto del golpe.
- [ ] Hacha con Shift: solo cae un tronco.
- [ ] Árbol gigante de jungla o casa de troncos grande: nunca se rompen más de 128 bloques.
- [ ] Funciona con tallos carmesí y distorsionados.
- [ ] Talar 20 troncos gasta 10 de durabilidad. En creativo no gasta.
- [ ] Golpear a un mob con el hacha hace 0.5 de daño. Minar piedra con ella es más lento que a mano.
- [ ] Mesa de encantar: solo Efficiency. Yunque: solo Efficiency y Mending.
- [ ] Reparación en yunque con lingote de dioritina funciona.
- [ ] Probar también en un servidor dedicado (`runServer`) con un cliente conectado.

---

## 7. Fuera del alcance de la v1 (no implementar)

Escudo de diorita, armadura, otras herramientas, bloques nuevos, cambios en variantes de diorita, otros idiomas, configuración por archivo. Se decidirán más adelante.
