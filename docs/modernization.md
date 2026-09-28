# Homely 0.3 · criação e aplicação

A navegação preserva o padrão visual azul/violeta, com cards de tema em proporção 0,57 (próxima a um celular), galerias deslizáveis e ícone adaptativo com margem interna de 9%.

## Conta
Foto pode ser enviada tocando no avatar ou em “Enviar foto de perfil”. A API valida propriedade e formato da imagem. O campo @ consulta /v1/usernames/availability após 450 ms sem digitação, cancela consultas anteriores e mostra disponível/ocupado/erro. O próprio nome continua disponível ao dono. A restrição única no banco continua sendo a validação final contra disputa simultânea.

## Criador
Escolha Tema completo, Wallpaper, Ícones ou Widget. Essa escolha abre a seção apropriada; outras peças continuam disponíveis. Sem wallpaper enviado, a cor de fundo vira uma imagem válida para a composição.
- Wallpaper estático e vídeo local.
- Imagens de ícones associadas a aplicativos instalados.
- Widgets de relógio, data, bateria, texto e imagem. Nome, texto, cores HEX, altura, fonte, cantos e ordem configuráveis.
- Grade de 3–6 colunas, ícones de 40–72 dp e nomes opcionais.
- Até oito screenshots, metadados, licença, importar/exportar .homely.
- Salvar privado, aplicar e publicar são ações independentes.

Widgets .hwidget são configurações JSON declarativas de até 64 KB, sem código executável. Imagens podem ser enviadas pelo editor; acompanham o pacote completo .homely. Uma configuração .hwidget de imagem requer selecionar sua imagem no editor. Até 12 widgets por tema.

## Homely Home
Uma Activity HOME/DEFAULT registrada funciona como launcher. A primeira aplicação solicita a escolha do Homely pelo sistema (RoleManager no Android 10+; configurações de início no Android 8/9). A escolha de launcher é sempre do usuário.
O tema aplicado modifica os ícones mapeados na tela inicial e na lista de aplicativos do Homely, os widgets definidos, cores, formato e grade. Apps sem ícone personalizado conservam seus ícones originais. Todos os apps continuam acessíveis e abrem seus aplicativos reais.
Vídeo é exibido sem som como fundo da tela inicial Homely. O wallpaper estático também é aplicado à tela inicial do sistema. A tela de bloqueio e a barra de notificações permanecem controladas pelo Android.
Widgets instalados de outros aplicativos podem ser adicionados em Personalizar > Adicionar widget de outro aplicativo, via AppWidgetHost e seleção/configuração do Android. Esses IDs e permissões são locais; não são transportáveis dentro de temas compartilhados.

## Privacidade local
Aplicar ou salvar uma criação copia seus arquivos para armazenamento privado do app. As cópias não dependem de arquivos temporários, login ou publicação. Perfil > Meus salvos neste aparelho permite aplicar, editar ou excluir. Um tema ativo não pode ser excluído antes de aplicar outro. Desinstalar o aplicativo remove essa biblioteca; exporte .homely para guardar fora dele.
A preparação é persistida antes da escolha do launcher, permitindo concluir a ativação se o Android abrir a tela inicial antes de devolver o resultado à Activity original.

## Formato 1.2
Mantém leitura de 1.0 e 1.1. Acrescenta widgets, layout e assets widget_image. Imagens dos widgets são vinculadas pelo ID do recurso, verificadas por tamanho/SHA-256, copiadas e exportadas junto com o tema. JSON inválido, tipos executáveis, tamanhos fora dos limites e imagens ausentes são recusados.

## Referências técnicas
- [Papel de tela inicial e consentimento do Android](https://developer.android.com/reference/android/app/role/RoleManager)
- [Hospedagem de widgets Android](https://developer.android.com/develop/ui/views/appwidgets/host)

Com Homely escolhido como launcher, a seleção múltipla na aba Ícones atualiza diretamente os ícones da composição ativa e guarda uma nova cópia privada. Em outros launchers, utiliza o fluxo de atalhos compatível com o Android.