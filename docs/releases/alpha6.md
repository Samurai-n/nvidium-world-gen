# Alpha 6 — interface simples e antecipação do carregamento

Nas opções gráficas Sodium/Reese's ficam ativar, predefinição, distância e velocidade. O botão Todas as configurações abre a tela completa; os controles técnicos permanecem no Mod Menu.

A velocidade usa uma escala de 1 a 64 e ajusta juntos os grupos de leitura, chunks por atualização, partes preparadas por quadro e tempo de trabalho. Não muda distância nem memória. Uma predefinição selecionada é aplicada ao salvar e prevalece sobre ajustes de velocidade feitos na mesma aplicação. Alterações entram ao reabrir o mundo.

O worker pode antecipar 64 resultados preparados, antes 32, mantendo o teto de 64 MiB. Velocidades acima de 32 permitem 128 pedidos pendentes, antes 64. Isso reduz esperas entre leitura e instalação; não elimina o custo de preparar e enviar geometria completa ao Nvidium.

O vídeo fornecido tem 8,63 segundos. Nas amostras a cada segundo, o contador mostra aproximadamente 139 a 186 FPS enquanto a área visível aumenta. Sem contadores de cache ou tempos por etapa, ele não permite atribuir a espera a um componente nem medir a aceleração desta alpha.

World Gen continua pendente. Alphas anteriores devem ser preservadas em releases/alpha-N.

Validação: 17 testes JUnit passaram, incluindo preparação antecipada de 64 chunks; testes reais com Cloth, Sodium/Reese's, cancelar/aplicar, velocidade, compartilhamento de configuração e persistência passaram. Log: build/alpha6-final-tests.log.

Teste com cópia descartável do cache, velocidade64, raio67, teto4096chunks/1536MiB: 2464 chunks restauradas em5,05s e3959 em10,21s. Nesta segunda amostra,13738 seções haviam sido enviadas ao Nvidium. Instalação máxima0,62ms, sem overruns, corrupção ou rejeição. Não mede geometria totalmente pronta nem é comparação controlada entre versões. Log: build/alpha6-corpus.log.

Instalada na instância Fabulously Optimized, sem alterar sua configuração. JAR e fontes arquivados em releases/alpha-6. SHA256 do JAR: BE2BDF3591C61CBF05E6C7CDD425C92F5DACA998BD4E4961ECC54D2285C96970.
