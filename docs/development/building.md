# Building from Source

## Requirements

- Java JDK 8+
- A local StarMade installation

## Setup

Set `starmade_root` in `gradle.properties` to your StarMade directory (with trailing `/`):

```properties
starmade_root=/path/to/StarMade/
```

## Build

```bash
./gradlew build
```

The JAR is output directly to your StarMade `mods/` folder.
