# BushLaunch - serwer znajomych

Mały serwer (Node.js 18+, **bez zależności**), który daje launcherowi:

- **zaproszenia do znajomych potwierdzane w launcherze** - drugi gracz widzi zaproszenie na liście znajomych i klika *Akceptuj* / *Odrzuć*,
- **status znajomych** (zielona kropka = w grze, niebieska = online, szara = offline),
- tożsamość gracza potwierdzaną przez **serwer sesji Mojang** - token gry nigdy nie trafia na ten serwer.

## Uruchomienie

```bash
node server.js
```

Zmienne środowiskowe (wszystkie opcjonalne):

| zmienna | domyślnie | opis |
|---|---|---|
| `PORT` | `8787` | port HTTP |
| `HOST` | `0.0.0.0` | adres nasłuchu |
| `DB_FILE` | `data.json` obok pliku | plik z danymi (znajomi, zaproszenia) |
| `MOJANG_SESSION_URL` | `https://sessionserver.mojang.com` | tylko do testów |

Dane zapisują się same w `data.json`. Kopię zapasową robisz kopiując ten jeden plik.

### Stałe działanie (przykład: pm2)

```bash
npm i -g pm2
pm2 start server.js --name bushlaunch-friends
pm2 save && pm2 startup
```

### HTTPS

Wystaw serwer przez HTTPS (nginx, Caddy albo Cloudflare Tunnel) i przekazuj nagłówek `X-Forwarded-For`.
Przykład Caddy:

```
friends.twojadomena.pl {
    reverse_proxy 127.0.0.1:8787
}
```

## Podłączenie launchera

Wystarczy podać adres serwera (bez końcowego `/`), np. `https://friends.twojadomena.pl`.
Są trzy sposoby, od najważniejszego:

1. plik `config.properties` launchera: `socialUrl=https://friends.twojadomena.pl`
2. wbudowany w jar plik `src/main/resources/social.properties`: `url=https://friends.twojadomena.pl`
   (workflow GitHub Actions tworzy go ze zmiennej repozytorium **FRIENDS_URL**:
   Settings > Secrets and variables > Actions > Variables)
3. brak adresu = launcher działa po staremu (lokalna lista znajomych bez potwierdzania)

## Jak to działa

1. Launcher prosi o losowy `serverId` (`GET /v1/auth/challenge`).
2. Launcher robi `join` na serwerze sesji Mojang z tym `serverId` (tak jak przy wejściu na serwer Minecrafta).
3. Serwer pyta Mojang (`hasJoined`), czy ten nick naprawdę dołączył - jeśli tak, wydaje sesję na 12 godzin.
4. Co ok. 20 s launcher wysyła `ping` (online / w grze) i dostaje listę znajomych, zaproszeń i statusów.
   Brak pingu przez 75 s oznacza offline.

## Limity

100 znajomych, 30 oczekujących wysłanych zaproszeń, 120 żądań na minutę na gracza.
