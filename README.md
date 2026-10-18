# Pool Aim Auto Beta 0.3 — atualização experimental

Atualização sobre a versão 0.2, em resposta aos testes no celular.

## Alterações

- A linha projetada é **branca, contínua e fina** (aproximadamente 1,5 pixel de tela), sem pontilhados, brilho, efeitos, bolinhas ou animação.
- O desenho é leve e transparente para liberar os toques do jogo.
- Um `AimStabilizer` reduz a troca brusca de direção entre quadros, exige duas leituras semelhantes para aceitar uma mudança grande e remove resultados antigos.
- Mantém a bolinha 8, o menu minimizável e a detecção experimental de linha da versão anterior.

## Limitações e próximos testes

A estabilização não corrige por si só a identificação da bola branca, de outras bolas ou do sentido certo da linha. O algoritmo visual atual pode ainda seguir linhas erradas, inverter a direção ou ser influenciado pela interface. Trajetórias de bola após colisões, velocidade, força e efeitos não são calculados. As rebatidas nas tabelas são apenas aproximações geométricas.

Para melhorar a precisão, serão necessárias capturas reais da partida com a mira original visível e validação no celular. **O APK 0.3 não foi compilado nem testado neste ambiente**: gere-o com GitHub Actions.

## Como compilar

Como a versão 0.2 já compila no repositório `8BallAuto`, é possível **substituir os arquivos correspondentes pelo conteúdo da pasta `app`** e manter o seu workflow que funcionou. Se preferir, envie o projeto completo para um repositório novo, mantendo `.github/workflows/android-apk.yml`, que usa Gradle 8.9 (não depende de `gradlew`). Depois acesse **Actions → Summary → Artifacts**.

Não misture versões de classes Java. Atualizar os arquivos `app/src/main/java/br/com/poolaim/` em conjunto.
