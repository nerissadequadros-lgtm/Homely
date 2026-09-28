# Formato de temas · Homely 1.4

Um arquivo `.homely` é um ZIP com um `manifest.json` UTF-8 na raiz e arquivos em `assets/`. Não contém código executável, APKs, scripts ou caminhos de rede obrigatórios.

```text
meu-tema.homely
├── manifest.json
└── assets/
    ├── <uuid>.png
    ├── <uuid>.mp4
    └── <uuid>.webp
```

## Manifesto

```json
{
  "schemaVersion": "1.1",
  "title": "Meu universo",
  "description": "Créditos e descrição do tema.",
  "category": "Minimal",
  "tags": ["violeta", "minimal"],
  "palette": {
    "accent": "#7965FF",
    "background": "#090C12",
    "surface": "#171C27"
  },
  "iconStyle": "rounded",
  "clockStyle": "thin",
  "assets": [{
    "id": "3af13e71-0e30-4b14-851f-79ba9c853108",
    "kind": "wallpaper",
    "name": "Wallpaper",
    "url": "assets/3af13e71-0e30-4b14-851f-79ba9c853108.png",
    "mime": "image/png",
    "sha256": "<64 caracteres hexadecimais do arquivo>",
    "size": 123456
  }],
  "compatibility": {
    "minAndroid": 26,
    "launcherRequiredForIconPack": true,
    "systemBars": "preview-only"
  },
  "license": "CC-BY-4.0"
}
```

`kind` aceita `wallpaper`, `live_wallpaper`, `icon` ou `screenshot`. Ícones incluem `packageName`, por exemplo `com.android.chrome`. Um vídeo MP4 tem tipo `live_wallpaper`; a capa estática `wallpaper` é obrigatória mesmo em temas animados.

Cores usam HEX com seis dígitos. `iconStyle`: rounded/circle/squircle. `clockStyle`: thin/bold/serif. As opções descrevem a prévia; aplicação global depende da compatibilidade real.

Licenças disponíveis: CC-BY-4.0, CC0-1.0, personal-use. O criador declara uma licença; isso não substitui a responsabilidade de possuir os direitos dos recursos.

Limites do cliente: 512 recursos, 50 MB por arquivo, 250 MB descomprimidos por pacote, manifesto de até 512 KB. Entradas duplicadas e caminhos fora de `assets/` são rejeitados. Todo recurso precisa bater com tamanho e SHA-256 declarados.

## Publicação

1. Autenticar em `POST /v1/auth/login` ou registrar uma conta.
2. Enviar cada arquivo em `POST /v1/media`, multipart, campo `file`.
3. Usar id, URL, MIME, tamanho e SHA-256 retornados na lista de recursos.
4. Enviar o manifesto para `POST /v1/themes`.
5. A API verifica que os recursos pertencem à conta e que seus metadados não foram trocados.

Na API, `url` aponta para `/media/<uuid>.<ext>`. No download do ZIP, ela é convertida para `assets/<uuid>.<ext>`. O Android importa os arquivos para o rascunho; publicar uma importação envia novas cópias pertencentes ao autor atual.

`GET /v1/themes/:id/download` exporta o tema. Componentes podem ser baixados individualmente pela URL de mídia. O importador aceita 1.0 até 1.4. Screenshots exigem 1.1; são limitados a oito imagens por tema. Ícones podem declarar category e style conforme a taxonomia em refinement.md. Screenshots representam a apresentação do tema, sem serem componentes aplicáveis.

O formato 1.2 adiciona widgets declarativos, layout e assets widget_image. Consulte modernization.md e as validações em server/src/manifest.ts.
## Extensões 1.3 e 1.4

1.3 adiciona `icon_source`, `iconEditor`, camadas individuais e paleta de cinco cores. 1.4 adiciona assets `font` (`font/ttf` e `font/otf`, até 5 MB), a biblioteca `fonts` e referências `fontId`, posições X/Y de ícones, modo livre/grade e dimensões da grade do widget, peso/alinhamento/opacidade/progresso por item. `layout.gestures` guarda ações e `layout.kit` guarda referência, página e retângulo normalizado de cada ícone/widget inicial. Fontes e imagens são copiadas no pacote; não depende do arquivo original no aparelho do criador.
