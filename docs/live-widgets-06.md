# Widgets vivos · Homely 0.6

## Uso

No Widget Studio, adicione um item, toque nele na prévia e personalize suas propriedades. O modo **Editar** seleciona e move camadas; **Testar** executa os toques reais. Salve privadamente, adicione como widget Android, flutue sobre a home ou inclua no kit do tema.

- Clima: escolha uma cidade, Celsius/Fahrenheit, Editorial/Compacto/Por hora; ligue/desligue cidade, condição, símbolo, mínima/máxima das próximas 24 h, umidade e vento. A prévia usa a previsão real. Toque no widget aplicado para detalhes, atualização manual e créditos.
- Relógio: hora real, 12/24 h, segundos e fuso IANA. O digital nativo usa TextClock. O analógico nativo segue o relógio Android; a versão desenhada no Homely Home tem estilo próprio.
- Calendário: mês real com atualização de data; toque para abrir a agenda instalada. O Homely não lê seus compromissos privados. Há abertura do formulário de evento quando usado o calendário interno de apoio.
- Telefone: discador vazio ou número configurado; nunca realiza ligação automaticamente.
- Atalho: escolha um app instalado. Atalhos de telefone/calendário/relógio de temas portáveis usam as ações Android correspondentes quando o pacote original não existe.
- Nota: toque no widget aplicado para editar e salvar. O conteúdo fica no aparelho.
- Bateria e contagem: dados do aparelho e tempo calculado, sem valores de demonstração.

## Dados e atualização

A VPS consulta [MET Norway](https://api.met.no/doc/TermsOfService) por HTTPS com identificação Homely, cache de pelo menos 30 minutos/respeitando Expires, If-Modified-Since, limite de tráfego, pedidos simultâneos agrupados por coordenada e fallback anterior por até seis horas. O app mantém cache local e indica dados anteriores; nunca inventa temperatura quando a rede falha. A temperatura apresentada é uma previsão do modelo para a hora atual, não leitura de um sensor do aparelho.

As 34.146 cidades vêm do dataset cities15000 do [GeoNames](https://www.geonames.org/), transformado em JSON no servidor. A busca não usa GPS nem envia consultas de cidade ao GeoNames. Ao selecionar uma cidade, suas coordenadas são usadas na consulta de previsão; essas coordenadas acompanham o tema caso seja publicado.

As duas fontes usam CC BY 4.0, com créditos no app e em server/data/ATTRIBUTION.txt. WEATHER_USER_AGENT permite configurar a identificação do serviço na VPS. O provedor pode ser trocado no backend sem atualização do APK.

Widgets nativos são atualizados pelo mecanismo do Android (período solicitado: 30 minutos), sujeito à economia de bateria. Relógios digitais continuam pelo TextClock. Editor/Home/overlay consultam dados em cache e renovam a previsão quando necessário. O overlay exige as permissões já descritas em studio-v3.md e continua restrito à home.

## Formato 1.5

WidgetLayer inclui weather, clock, phoneNumber, tapAction e monthOffset, com valores padrão para temas antigos. As opções de cada item são independentes. O servidor valida coordenadas, formatos e fusos. Nenhum script acompanha o pacote.
