# Validação
## 0.2.0 — concluída em 21/09/2026
- API: 10 testes passaram, incluindo integração com PostgreSQL em homely_test.
- Android: compilação, 3 testes JVM, 4 testes em Android 15 e lint concluídos; zero erros de lint.
- Emulador: swipe dentro do card, recolhimento/retorno da navegação, seleção de dois ícones e confirmação real do launcher para adicionar atalho.
- Evidências em artifacts/screenshots/refined-*.png.

## 0.3.0 — validação em andamento
- API: 12 testes passaram com PostgreSQL real separado, incluindo disponibilidade de @, propriedade de mídia e definições de widgets.
- API /health informa 0.3.0; catálogo editorial atualizado para formato 1.2 com widgets funcionais.
- Arquivo .homely: testes JVM de integridade, travessia de diretório e preservação de widgets/imagens passaram.
- Android registrou Homely como candidato HOME; a confirmação do sistema foi verificada.
- Verificação final do APK, ativação após escolha de launcher, biblioteca privada e screenshots ainda em execução.
