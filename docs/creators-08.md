# Criadores 0.8

Os criadores usam o cabeçalho único do aplicativo e etapas numeradas. O perfil também substitui o nome Homely no cabeçalho. As ferramentas detalhadas aparecem apenas na etapa ou no inspetor correspondente; foram removidos os subtítulos decorativos.

- Widgets: Base, Itens, Fundo e Revisão. Canvas com arraste e alça de redimensionamento, largura e altura independentes (64–800), imagem de fundo com recorte não destrutivo, relógios, calendários, indicadores reais, ícones e elementos salvos.
- Elementos: Base, Estados, Ação, Animação e Salvar. Uma peça por criação. Trocar o modelo substitui a peça; não acrescenta uma composição. Wi-Fi, bateria, RAM, CPU do Homely, relógio, calendário e outros modelos podem ter aparência e ação próprias.
- Ícones: Base, Camadas, Estados e Revisão. Fundo e primeiro plano independentes, formas, desenho de formas no canvas, importação de imagem/SVG, cores, gradientes, opacidade, posição e alinhamento. Estados padrão, pressionado e desativado; revisão nas máscaras círculo, quadrado e squircle em dois tamanhos. A biblioteca preserva o desenho editável e as imagens de cada estado.
- Temas: Papel de parede, Paleta, Ícones, Elementos e widgets, Revisão. Reutilização por pasta, layout livre e vínculo opcional dos widgets à paleta. Gestos, grade, animações e comportamento continuam disponíveis nas configurações da revisão.

O diálogo de salvamento permite escolher uma pasta ou criar outra. Cada criação possui arquivos próprios. Os estados dos ícones, imagens dos elementos, fundo do widget e dimensões livres acompanham o pacote `.homely`. Formatos anteriores continuam sendo lidos; composições antigas permanecem compatíveis com o renderizador.

## Descoberta

O sino abre a central de notificações e mostra itens não lidos. A API agenda uma seleção por dia em horário local variável entre 08:00 e 23:00, alterna mensagens e personaliza recomendações com visualizações, duração, reaplicações, curtidas, coleções, criadores e características dos temas. É possível desligar as sugestões. O Android pode atrasar a entrega em segundo plano; a central preserva os itens disponíveis.

## Limites do Android

As cores da barra de status pertencem ao Homely Home; um launcher comum não pode recolorir a cortina de notificações do sistema. Wi-Fi e Bluetooth usam os painéis permitidos pelo Android. CPU representa o processo Homely, sem inventar uma leitura global. Dados indisponíveis têm estado próprio.

Animações, gestos internos, pressão longa e áreas de toque funcionam no Homely Home. Widgets em outro launcher seguem as capacidades de RemoteViews: aparência atualizada por snapshot e ação de toque, com relógio nativo quando aplicável. Atalhos externos usam o estado padrão do ícone; estados pressionado/desativado funcionam no Homely Home.

## Verificação

As suítes cobrem persistência do pacote, independência dos arquivos, importação SVG, renderização de estados, fluxo unitário com salvamento em pasta e atualização das ações gestuais durante a prévia. Resultados finais são registrados na entrega junto ao APK de desenvolvimento.
