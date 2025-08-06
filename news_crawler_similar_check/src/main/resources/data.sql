-- 카테고리 초기 데이터
INSERT INTO news_category (category_name, created_at) VALUES 
('정치', NOW()),
('경제', NOW()),
('사회', NOW()),
('생활/문화', NOW()),
('세계', NOW()),
('IT/과학', NOW());

-- 언론사 초기 데이터 (허용된 언론사)
INSERT INTO press (press_name, blacklisted) VALUES 
('연합뉴스', false),
('매일경제', false),
('한국경제', false),
('서울경제', false),
('경향신문', false),
('한겨레', false),
('동아일보', false),
('조선일보', false),
('중앙일보', false),
('한국일보', false),
('국민일보', false),
('문화일보', false),
('서울신문', false),
('경기일보', false),
('JTBC', false),
('TV조선', false),
('채널A', false),
('MBN', false),
('뉴스1', false),
('KBS', false),
('MBC', false),
('SBS', false),
('YTN', false),
('머니투데이', false),
('파이낸셜뉴스', false),
('이데일리', false),
('아시아경제', false),
('BBC News 코리아', false),
('미디어오늘', false),
('뉴시스', false),
('SBS Biz', false),
('조선비즈', false),
('헤럴드경제', false),
('오마이뉴스', false),
('지디넷코리아', false), 
('동아사이언스', false),
('프레시안', false),
('전자신문', false)

-- 블랙리스트 언론사 예시 (필요시 사용)
-- INSERT INTO press (press_name, blacklisted) VALUES 
-- ('문제언론사1', true),
-- ('문제언론사2', true); 