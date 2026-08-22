# 🎬 StemSpoor — Amptelike Video Walkthrough & Bemarkingskrip

**Titel-idee:** *StemSpoor: Die Privaat Kognitiewe Brein vir Ingenieurs (Volledige Funksie-Toets & Oorsig)*  
**Aanbieder:** Jan  
**Tydsduur:** ~10–12 minute  
**Formaat:** Skermopname (Android-toestel + Obsidian op rekenaar) gekombineer met 'n praatkop/kamera-opstelling.

---

## 🎯 Doelwitte van die Video
1. **Regte Funksietoets:** Bewys dat elke stelsel (VAD, Voice Gate, Diarization, Dual ASR, Semantiese Soektog, Waghond) foutloos intyds werk.
2. **Bemarking & Waardeproposisie:** Verduidelik *hoekom* StemSpoor radikaal verskil van gewone stemopnemers (Privaatheid, §201 StGB voldoening, Plaaslike Vektore, Obsidian Vault-integrasie).
3. **Outentieke Ingenieurs-narratief:** 'n Pragmatiese, reguit demonstrasie gebou op die *Boer Maak 'n Plan*-etos.

---

## 📋 Produksie-Oorsig & Toneelrooster

```
[00:00 - 01:15]  Toneel 1: Die Haakplek & Die Probleem (Privaatheid vs. Geheue)
[01:15 - 02:45]  Toneel 2: 1-Tik Quick Settings Tile & Silero VAD Agtergrond-Monitering
[02:45 - 04:30]  Toneel 3: Voice Gate & Die 10-Sekonde Wagbuffer (§201 StGB Toets)
[04:30 - 05:45]  Toneel 4: Telefoonoproep-Waghond (Outo-Pouse & Hervat)
[05:45 - 07:15]  Toneel 5: Spreker-Diarization & Aaneenlopende Stemprofiel-Leer
[07:15 - 09:00]  Toneel 6: Dubbele Transkripsie (Vanlyn SenseVoice vs. Wolk Gemini Flash)
[09:00 - 10:15]  Toneel 7: Obsidian Vault & Plaaslike Semantiese Vektor-Soektog
[10:15 - 11:15]  Toneel 8: Berging-Integriteit Waghond (Outo-Herstel Toets)
[11:15 - 12:00]  Toneel 9: Afsluiting & Visie
```

---

## 🎥 Toneel-vir-Toneel Draaiboek

### Toneel 1: Die Haakplek & Die Probleem (00:00 – 01:15)
* **Visueel:** Kamera op Jan by sy lessenaar met sy foon langs sy rekenaar. Op die rekenaarskerm is 'n komplekse simulasie/argitektuurmodel oop.
* **Teks op Skerm:** `StemSpoor: Private Ambient Voice Intelligence`

> **Jan (Spraak):**  
> *"As ingenieur en pa is my gedagtes heeldag vol idees, besluite en tegniese gesprekke. Maar die oomblik as jy 'n goeie idee hardop sê terwyl jy ry of stap, is dit binne vyf minute vergete.*  
>  
> *Tradisionele stemnotas werk nie – jy vergeet om op te neem, die lêers hoop net op, en bowenal: in Duitsland en Europa het jy te doen met streng privaatheidswette soos §201 StGB. Jy mág nie net mense om jou sonder toestemming opneem nie.*  
>  
> *Daarom het ek **StemSpoor** gebou. Dit is nie net 'n opnemer nie; dit is 'n privaat, plaaslike kognitiewe brein wat jou stem herken, vreemdelinge se privaatheid wetlik beskerm, en outomaties jou daaglikse notas in Obsidian struktureer. Kom ek wys jou presies hoe elke funksie werk."*

---

### Toneel 2: 1-Tik Quick Settings Tile & Silero VAD (01:15 – 02:45)
* **Visueel:** Skermopname van die Android-foon. Jan vee van bo af af om die Quick Settings-paneel te wys. Hy tik op die **StemSpoor Mic Tile**.
* **Aksie:** Die Tile skakel dadelik oor na `StemSpoor Active (Recording active)`. Die app maak oop op die Hoofskerm met die Kalahari-sonsondergang golfvorm-visualiseerder.

> **Jan (Spraak):**  
> *"Eerstens: Geen wrywing nie. Ek hoef nie eers die app oop te maak nie. Vanuit my Android Quick Settings tik ek een keer op die **StemSpoor Tile**.*  
>  
> *In die agtergrond hardloop **Silero VAD** (Voice Activity Detection). Kyk wat gebeur as ek stilbly... die mikrofoon luister slegs in RAM teen 16kHz, maar skryf geen leë stilte na die skyf nie. Sodra ek begin praat, lig die Kalahari-sonsondergang visualisering op en begin die oudio intyds verwerk. Dit spaar gigagrepe berging en hou batteryverbruik minimaal."*

---

### Toneel 3: Voice Gate & Die 10-Sekonde Wagbuffer (§201 StGB) (02:45 – 04:30)
* **Visueel:** Jan gaan na `Settings` $\rightarrow$ `Voice Gate & Legal Privacy`. Hy wys die geaktiveerde Voice Gate en sy eie ingeskrewe stemprofiel.
* **Toets-aksie:** 
  1. Jan praat self: Die skerm wys `Voice Gate: ALLOWED (Jan - 0.91 confidence)`.
  2. Jan speel 'n stemgreep van 'n vreemde stem op 'n tweede toestel: Die skerm wys `Voice Gate: DENIED (Unauthorized voice discarded)`.

> **Jan (Spraak):**  
> *"Hier is die deurbraak vir privaatheid: **Die Voice Gate**. Onder Artikel 201 van die Duitse Strafwetboek is dit onwettig om vertroulike spraak van derdepartye sonder toestemming vas te lê.*  
>  
> *StemSpoor los dit op met 'n **10.24-sekonde sirkulêre RAM-wagbuffer**. Wanneer klank begin, word dit slegs in vlugtige geheue gehou. Die AI-enjin onttrek 'n 192-dimensionele akoestiese inbedding en vergelyk dit met my gemagtigde stemprofiel.*  
>  
> *As dit ek is – word die buffer terugwerkend na die WAV-lêer geskryf. As 'n vreemdeling praat wat nie toestemming gegee het nie – word die hele klankgreep stilweg uit die geheue gegooi sonder dat 'n enkele greep ooit die skyf tref. Dis wetlike voldoening deur wiskunde en argitektuur."*

---

### Toneel 4: Telefoonoproep-Waghond (04:30 – 05:45)
* **Visueel:** 'n Inkomende oproep verskyn op die foon. 
* **Aksie:** Die opname fluit dadelik die huidige WAV-deel skoon af en gaan in `PAUSED (Call in progress)` modus. Wanneer die oproep afgelui word, hervat StemSpoor outomaties sonder enige gebruikersaksie.

> **Jan (Spraak):**  
> *"Wat gebeur as iemand jou bel terwyl StemSpoor opneem? Ons **Telephony Call Interruption Watchdog** monitor stelsel-oproepe.*  
>  
> *Sodra die foon lui, word die mikrofoon dadelik vrygestel vir die oproep, en die huidige oudio-segment word netjies afgesluit. Sodra jy ophang, skakel StemSpoor dadelik weer aan. Jy hoef nooit te onthou om jou opname weer te begin nie."*

---

### Toneel 5: Spreker-Diarization & Aaneenlopende Leer (05:45 – 07:15)
* **Visueel:** Jan wys die `Recordings`-oortjie waar 'n opname met twee persone pas voltooi is.
* **Aksie:** Hy maak die `.json` sidecar oop: Segmente word outomaties gemerk as `Jan` en `Speaker 2`.

> **Jan (Spraak):**  
> *"Sodra 'n opname voltooi is, tree ons vanlyn **Speaker Diarization Engine** in werking. Dit sny die spraak in oorvleuelende vensters op en groepeer wie wanneer gepraat het.*  
>  
> *Wat meer is: StemSpoor het **aaneenlopende leer**. As ek in Afrikaans, Engels of Duits praat, pas die stelsel outomaties my globale en taalsensitiewe stem-sentroïede aan. Hoe meer ek die app gebruik, hoe skerper word sy herkenning."*

---

### Toneel 6: Dubbele Transkripsie-Pyplyn (07:15 – 09:00)
* **Visueel:** Jan wys die `Settings`-skerm met die 3 transkripsiekeuses: `Local Only`, `Google AI Studio`, en `Smart Hybrid`.
* **Aksie:** 
  1. Hy wys 'n vanlyn transkripsie met **SenseVoice-Small** (ultra-vinnig op SVE).
  2. Hy wissel na **Smart Hybrid** met sy Google AI Studio API-sleutel: Wys hoe **Gemini 2.5 Flash** die klank transkribeer én 'n bondige opsomming met aksie-items skep.

> **Jan (Spraak):**  
> *"Vir transkripsie gee StemSpoor jou die beste van beide wêrelde met die **Dual Transcription Pipeline**.*  
>  
> *As jy 100% vanlyn wil bly sonder dat enige greep jou foon verlaat, gebruik ons die hoëspoed **SenseVoice-Small** en Whisper-modelle. Dit hardloop direk op jou SVE teen 15x intydse spoed en hanteer Afrikaanse taalmenging soos 'n droom.*  
>  
> *As jy internet het en dieper insigte soek, kies jy **Smart Hybrid**. Dit stuur die klank na Google AI Studio se **Gemini Flash**, wat nie net letterlik transkribeer nie, maar ook dadelik 'n opsomming, besluitelys en aksiepunte uittrek."*

---

### Toneel 7: Obsidian Vault & Semantiese Vektor-Soektog (09:00 – 10:15)
* **Visueel:** Jan skuif na sy rekenaar en wys sy **Obsidian Vault**. Die daaglikse nota (`2026-08-22.md`) is reeds opgedateer met tydstempels, spreker-etikette en `[[Wikilinks]]`.
* **Aksie op Foon:** Jan gaan na die `Vault`-oortjie in StemSpoor en soek na 'n konsep: *"wiel dinamika en skorsing"*.
* **Resultaat:** Die soekenjin bring die spesifieke opname na bo met 'n `94% SEMANTIC_CONCEPT` passing, al het die opname net gepraat van *"Simscape Multibody suspensie-toets"*.

> **Jan (Spraak):**  
> *"Elke voltooide transkripsie word outomaties uitgevoer na jou plaaslike **Obsidian Vault** met skoon Markdown en tweerigting `[[Wikilinks]]`.*  
>  
> *Maar die ware towerkrag is ons **Hybrid Semantic Search Engine**. Ons stoor 384-dimensionele BGE-vektore direk in die plaaslike SQLite-databasis. As ek soek na 'n konsep, soek dit nie net na presiese woorde nie – dit verstaan die wiskundige betekenis van my woorde en vind die regte stemnota onmiddellik."*

---

### Toneel 8: Berging-Integriteit Waghond (10:15 – 11:15)
* **Visueel:** Jan forseer die app om toe te maak (Force Stop in Android-instellings) terwyl 'n opname loop.
* **Aksie:** Hy heropen StemSpoor. Die logs wys: `StorageIntegrityWatchdog: Repaired WAV header in-place (DataSize corrected), Room SQLite resynced`. Die oudio speel perfek af sonder korrupsie!

> **Jan (Spraak):**  
> *"As ingenieurs bou ons vir mislukking. Wat gebeur as jou foon se battery vrek of die stelsel die app doodmaak terwyl jy opneem? Gewoonlik is die WAV-lêer korrup en onbruikbaar.*  
>  
> *StemSpoor het 'n ingeboude **Storage Integrity Watchdog**. Met die volgende herbegin inspekteer dit elke lêer, herstel die 44-greep RIFF-kopskrif intyds sonder om oudio te verloor, en hersinkroniseer die databasis. Jou data is altyd veilig."*

---

### Toneel 9: Afsluiting & Oproep tot Aksie (11:15 – 12:00)
* **Visueel:** Jan terug op kamera met die StemSpoor-logo en GitHub-skakel op die skerm.
* **Teks op Skerm:** `Open Source. Private. Built for Thinkers.`

> **Jan (Spraak):**  
> *"StemSpoor is nie net 'n toepassing nie – dit is 'n betroubare verlengstuk van hoe ek dink, werk en my daaglikse lewe organiseer. Geen maandelikse intekeninge nie, geen wolk-dwang nie, en volle eienaarskap van jou data.*  
>  
> *Die projek is oopbron en beskikbaar op GitHub. Bou dit, toets dit self, en maak jou stem 'n blywende spoor. Dankie dat julle gekyk het!"*

---

## 🛠️ Toets-Kontrolelys voor Opname

- [ ] Installeer nuutste APK (`assembleDebug` / `main`).
- [ ] Bevestig mikrofoon- en bergingstoegangsregte op toetsfoon.
- [ ] Skryf 1 stemprofiel in (`Jan`) onder Settings.
- [ ] Toets Quick Settings Tile aan/af skakeling.
- [ ] Maak seker Google AI Studio API-sleutel is ingevoer vir die Gemini-demo.
- [ ] Koppel die foon se `Documents/RecMe/vault` aan Obsidian om die regstreekse Markdown-opdaterings te wys.
