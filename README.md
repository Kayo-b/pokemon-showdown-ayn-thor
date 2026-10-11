AYN Thor edition
========================================================================

This fork adds **Showdown Thor** ([`ayn-thor/`](ayn-thor/)), an Android app for playing
Pokémon Showdown on the **AYN Thor** dual-screen handheld with its built-in gamepad:

- **Top screen:** the official Showdown client. During a battle it shows only the battle scene, scaled up to fill the
  screen; X (or the panel's "Log" button) pops up an overlay with the tabs, battle log/chat and controls. Ads are blocked.
- **Bottom screen:** a battle panel with your moves (type, power, accuracy, effectiveness), switches, targets,
  Tera/Mega/Z/Dynamax, team preview, timer and forfeit. In the teambuilder it previews the Pokémon, move, item or
  ability under the highlight.
- **Gamepad:** everything is reachable with the D-pad and buttons; Select moves control between the screens, Start opens
  the app menu.

It plays on the official server (`play.pokemonshowdown.com`) by default; the server code in this repository is unchanged.

### Install on the AYN Thor

1. Build the APK (JDK 17 + Android SDK 35): `cd ayn-thor && ./gradlew assembleRelease`
   → `ayn-thor/app/build/outputs/apk/release/app-release.apk`
2. Copy the APK to the Thor and open it from a file manager, allowing "Install unknown apps" when asked; or, with USB
   debugging on (Settings → About → tap Build number 7 times → Developer options → USB debugging):
   `adb install -r ayn-thor/app/build/outputs/apk/release/app-release.apk`
3. Launch **Showdown Thor** from the top screen. The battle panel opens on the bottom screen automatically.

See [`ayn-thor/README.md`](ayn-thor/README.md) for the full control list and settings.


Pokémon Showdown
========================================================================

Navigation: [Website][1] | **Server repository** | [Client repository][2] | [Dex repository][3]

  [1]: http://pokemonshowdown.com/
  [2]: https://github.com/smogon/pokemon-showdown-client
  [3]: https://github.com/Zarel/Pokemon-Showdown-Dex

[![Build Status](https://github.com/smogon/pokemon-showdown/workflows/Node.js%20CI/badge.svg)](https://github.com/smogon/pokemon-showdown/actions?query=workflow%3A%22Node.js+CI%22)
[![Dependency Status](https://img.shields.io/librariesio/github/smogon/pokemon-showdown)](https://libraries.io/github/smogon/pokemon-showdown)


Introduction
------------------------------------------------------------------------

Pokémon Showdown is many things:

- A **website** you can use for Pokémon battling

  - http://pokemonshowdown.com/

- A **JavaScript library** for simulating Pokémon battles and getting Pokédex data

  - [sim/README.md](./sim/README.md)

- Some **command-line tools** for simulating Pokémon battles (which can be used in non-JavaScript programs)

  - [COMMANDLINE.md](./COMMANDLINE.md)

- A **web API** for the web site for Pokémon battling

  - [pokemon-showdown-client: WEB-API.md](https://github.com/smogon/pokemon-showdown-client/blob/master/WEB-API.md)

- A **game server** for hosting your own Pokémon Showdown community and game modes

  - [server/README.md](./server/README.md)

Pokémon Showdown simulates singles, doubles and triples battles in all the games out so far (Generations 1 through 9).


Documentation quick links
------------------------------------------------------------------------

* [PROTOCOL.md][4] - How the client and server communicate with each other.
* [sim/SIM-PROTOCOL.md][5] - The part of the protocol used for battles and battle messages.
* [CONTRIBUTING.md][6] - Useful code standards to understand if you want to send pull requests to PS (not necessary if you're just using the code and not planning to contribute back).
* [ARCHITECTURE.md][7] - A high-level overview of how the code works.
* [Bot FAQ][8] - An FAQ compiled by Kaiepi regarding making Pokemon Showdown bots - mainly chatbots and battle bots.

  [4]: ./PROTOCOL.md
  [5]: ./sim/SIM-PROTOCOL.md
  [6]: ./CONTRIBUTING.md
  [7]: ./ARCHITECTURE.md
  [8]: https://gist.github.com/Kaiepi/becc5d0ecd576f5e7733b57b4e3fa97e


Community
------------------------------------------------------------------------

PS has a built-in chat service. Join our main server to talk to us!

You can also visit the [Pokémon Showdown forums][9] for discussion and help.

  [9]: https://www.smogon.com/forums/forums/pok%C3%A9mon-showdown.209/

If you'd like to contribute to programming and don't know where to start, feel free to check out [Ideas for New Developers][10].

  [10]: https://github.com/smogon/pokemon-showdown/issues/2444


License
------------------------------------------------------------------------

Pokémon Showdown's server is distributed under the terms of the [MIT License][11].

  [11]: ./LICENSE


Credits
------------------------------------------------------------------------

Owner

- Guangcong Luo [Zarel] - Development, Design, Sysadmin

Staff

- Andrew Werner [HoeenHero] - Development
- Annika L. [Annika] - Development
- Chris Monsanto [chaos] - Development, Sysadmin
- Kris Johnson [dhelmise] - Development
- Leonard Craft III [DaWoblefet] - Research (game mechanics)
- Mathieu Dias-Martins [Marty-D] - Research (game mechanics), Development
- Mia A [Cassiopeia] - Development

Contributors

- See http://pokemonshowdown.com/credits
