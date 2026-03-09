function elcosCalcApp() {
    return {
        // --- ОБЩИЕ ПАРАМЕТРЫ (C7, C8) ---
        euroRate: 621,
        elcosK: 1.6,

        // --- ГАБАРИТЫ ШКАФА (B12, C12, D12, E12) ---
        heightMm: 2100,
        widthMm: 800,
        depthMm: 400,
        thicknessMm: 2, // Основной ввод толщины (E12/C72)

        // --- РАСХОДНИКИ (F15..F55) ---
        materials: [
            { id: "mat15", name: "Уплотнительная резина полиуретановая наливная", qty: 5.8, priceEuro: 0.34 },
            { id: "mat16", name: "Шарнир стандартный SR 408-1 DIL-1 :", qty: 0, priceEuro: 0.82 },
            { id: "mat17", name: "Шарнир стандартный SR 408-2", qty: 4, priceEuro: 1.45 },
            { id: "mat18", name: "Замок тяговый 002-5 + SR 303-2 +SR 302-1 + SR-303-1 SERMAK", qty: 1, priceEuro: 12.6 },
            { id: "mat19", name: "Замок SR 299-1", qty: 0, priceEuro: 1.75 },
            { id: "mat20", name: "Замок Мастер ключ IP65: 201-1+201-TK 1 (замки с разными ключами)", qty: 0, priceEuro: 1.95 },
            { id: "mat21", name: "Кармашек ELCOS", qty: 0, priceEuro: 0.96 },
            { id: "mat22", name: "Наклейка знак \"молния\":", qty: 1, priceEuro: 0.3 },
            { id: "mat23", name: "Наклейка знак \"земля\":", qty: 1, priceEuro: 0.3 },
            { id: "mat24", name: "Провод заземления ПВ-3-20 см 6мм2:", qty: 2, priceEuro: 0.25 },
            { id: "mat25", name: "Наконечник кольцевой 6*6 мм2", qty: 4, priceEuro: 0.05 },
            { id: "mat26", name: "М6 гайка:", qty: 20, priceEuro: 0.03 },
            { id: "mat27", name: "М6 болт с потайной головкой", qty: 20, priceEuro: 0.15 },
            { id: "mat28", name: "М6*15 мм чакма вида", qty: 3, priceEuro: 0.8 },
            { id: "mat29", name: "М8 болт грибок", qty: 3, priceEuro: 0.1 },
            { id: "mat30", name: "М8 гайка", qty: 3, priceEuro: 0.02 },
            { id: "mat31", name: "М8*20мм шестигранный болт", qty: 3, priceEuro: 0.13 },
            { id: "mat32", name: "Утеплитель Самокл. С Фольгой, толщиной 19мм", qty: 2, priceEuro: 8 },
            { id: "mat33", name: "PG16 IEK", qty: 0, priceEuro: 2 },
            { id: "mat34", name: "Петля SERMAK 730-V1", qty: 1, priceEuro: 18.9 },
            { id: "mat35", name: "Окошко SR 501-2", qty: 0, priceEuro: 9.97 },
            { id: "mat36", name: "Сальник PG13,5 IP54 d отверст. (20мм.) d проводника 6-12мм EKF PROxima", qty: 0, priceEuro: 0.1525 },
            { id: "mat37", name: "Сальник PG16 IP54 d отверст. (21мм.) d проводника 10-14мм EKF PROxima", qty: 0, priceEuro: 0.185 },
            { id: "mat38", name: "Сальник PG19 IP54 d отверст. (24мм.) d проводника 12-15мм EKF PROxima", qty: 0, priceEuro: 0.21 },
            { id: "mat39", name: "Сальник PG21 IP54 d отверст. (27мм.) d проводника 13-18мм EKF PROxima", qty: 0, priceEuro: 0.25 },
            { id: "mat40", name: "Сальник PG25 IP54 d отверст. (30мм.) d проводника 16-21мм EKF PROxima", qty: 0, priceEuro: 0.315 },
            { id: "mat41", name: "Сальник PG29 IP54 d отверст. (36мм.) d проводника 18-25мм EKF PROxima", qty: 0, priceEuro: 0.5125 },
            { id: "mat42", name: "Сальник PG36 IP54 d отверст. (46мм.) d проводника 22-32мм EKF PROxima", qty: 0, priceEuro: 0.905 },
            { id: "mat43", name: "Сальник PG42 IP54 d отверст. (53мм.) d проводника 32-38мм EKF PROxima", qty: 0, priceEuro: 1.0525 },
            { id: "mat44", name: "Сальник PG48 IP54 d отверст. (59мм.) d проводника 37-44мм EKF PROxima", qty: 0, priceEuro: 1.1375 },
            { id: "mat45", name: "Сальник MG12 (внутренний - Ø7,6-4,6)IP68", qty: 0, priceEuro: 0.305 },
            { id: "mat46", name: "Сальник MG16 (внутренний - Ø10-6) IP68", qty: 0, priceEuro: 0.4325 },
            { id: "mat47", name: "Сальник MG20 (внутренний - Ø14-9) IP68", qty: 0, priceEuro: 0.595 },
            { id: "mat48", name: "Сальник MG25 (внутренний - Ø18-13) IP68", qty: 0, priceEuro: 0.78 },
            { id: "mat49", name: "Сальник MG32 (внутренний - Ø25-18) IP68", qty: 0, priceEuro: 1.355 },
            { id: "mat50", name: "Сальник MG40 (внутренний - Ø30-24) IP68", qty: 0, priceEuro: 2.145 },
            { id: "mat51", name: "Сальник MG50 (внутренний - Ø39-30) IP68", qty: 0, priceEuro: 2.835 },
            { id: "mat52", name: "Сальник MG63 (внутренний - Ø49-39) IP68", qty: 0, priceEuro: 4.1375 },
            { id: "mat53", name: "Крепеж для фальш панелей SR-506(25)", qty: 0, priceEuro: 0.11097 },
            { id: "mat54", name: "Петля MESAN 340.20.011 -1м", qty: 0, priceEuro: 14.6 },
            { id: "mat55", name: "Дин рейка -1м", qty: 0, priceEuro: 0.775 }
        ],

        // --- ПАНЕЛИ (Развертки) ---
        panels: [
            { name: "Передняя и задняя дверь", qty: 2, wCm: 80, hCm: 210 },
            { name: "Боковые стенки", qty: 2, wCm: 40, hCm: 210 },
            { name: "Верх низ стенки", qty: 2, wCm: 40, hCm: 80 },
            { name: "Фальш панель", qty: 1, wCm: 80, hCm: 210 },
            { name: "Внутренность", qty: 0.5, wCm: 80, hCm: 210 }
        ],

        // --- МЕТАЛЛ И ПОКРАСКА ---
        paintConsumptionPerM2: 0.3,
        paintPriceTengePerKg: 2500,
        metalPriceTengePerKg: 460,

        // --- ЛОГИСТИКА И УПАКОВКА ---
        packFoamQty: 1,
        packFoamEuroPrice: 15,
        packWoodQty: 0,
        packWoodEuroPrice: 45,
        railTariffTengePerKg: 0,

        // --- КОНСТАНТЫ ИЗ EXCEL ---
        OVERHEAD_K: 1,
        AREA_K: 1.55,
        SHEET_CM2: 31250,
        DENSITY: 7.9,
        RAIL_K: 1.2,

        // ==========================================
        // ВЫЧИСЛЯЕМЫЕ СВОЙСТВА (Getters)
        // ==========================================

        get volumeM3() {
            return (this.heightMm * this.widthMm * this.depthMm) / 1_000_000_000;
        },
        get areaM2() {
            return (((this.heightMm * this.widthMm) + (this.widthMm * this.depthMm) + (this.heightMm * this.depthMm)) * 2) / 1_000_000;
        },

        // Расходники
        get materialsEuroSum() {
            return this.materials.reduce((sum, m) => sum + ((Number(m.qty) || 0) * (Number(m.priceEuro) || 0)), 0);
        },

        // Панели
        get panelAreas() {
            return this.panels.map(p => (Number(p.qty) || 0) * (Number(p.wCm) || 0) * (Number(p.hCm) || 0));
        },
        get areaRawCm2() {
            return this.panelAreas.reduce((a, b) => a + b, 0);
        },
        get areaAdjCm2() {
            return this.areaRawCm2 * this.AREA_K;
        },
        get metalSheets() {
            return this.areaAdjCm2 / this.SHEET_CM2;
        },

        // Итоги по металлу и краске
        get massKg() {
            return (this.areaAdjCm2 * (this.thicknessMm / 10) * this.DENSITY) / 1000;
        },
        get metalCostTenge() {
            return this.metalPriceTengePerKg * this.massKg;
        },
        get paintCostTenge() {
            return ((this.areaRawCm2 / 10000) * 2) * this.paintConsumptionPerM2 * this.paintPriceTengePerKg;
        },
        get montageCostTenge() {
            return this.materialsEuroSum * this.euroRate;
        },
        get materialsCostTenge() {
            return this.metalCostTenge + this.paintCostTenge + this.montageCostTenge;
        },
        get overheadCostTenge() {
            return this.materialsCostTenge * this.OVERHEAD_K;
        },
        get elcosBasePriceTenge() {
            const C78 = this.materialsCostTenge;
            const C79 = this.overheadCostTenge;
            return C79 + (C78 * this.elcosK - C78);
        },

        // Итоги по логистике
        get packFoamEuro() {
            return this.packFoamQty * this.packFoamEuroPrice * this.volumeM3;
        },
        get packWoodEuro() {
            return this.packWoodQty * this.packWoodEuroPrice * this.volumeM3;
        },
        get packCostTenge() {
            return (this.packFoamEuro + this.packWoodEuro) * this.euroRate;
        },
        get railCostTenge() {
            return this.massKg * this.railTariffTengePerKg * this.RAIL_K;
        },

        // ФИНАЛЬНАЯ ЦЕНА
        get finalPriceTenge() {
            return Math.round(this.elcosBasePriceTenge + this.packCostTenge + this.railCostTenge);
        },

        // --- МЕТОДЫ ---
        fmtNum(val, decimals = 2) {
            if (!Number.isFinite(val)) return "0";
            return Number(val).toLocaleString('ru-RU', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
        },

        async addToCart() {
            if (this.heightMm <= 0 || this.widthMm <= 0 || this.depthMm <= 0 || this.thicknessMm <= 0) {
                alert("Проверьте габариты и толщину металла: значения должны быть > 0");
                return;
            }

            const payload = {
                heightMm: this.heightMm,
                widthMm: this.widthMm,
                depthMm: this.depthMm,
                thicknessMm: this.thicknessMm,
                
                frontBackQty: this.panels[0].qty,
                sideQty: this.panels[1].qty,
                topBottomQty: this.panels[2].qty,
                falsePanelQty: this.panels[3].qty,
                innerQty: this.panels[4].qty,

                paintKgPerM2: this.paintConsumptionPerM2,
                paintPrice: this.paintPriceTengePerKg,
                metalPrice: this.metalPriceTengePerKg,
                complexityK: this.OVERHEAD_K,
                
                unitPriceTenge: this.finalPriceTenge,

                results: {
                    areaM2: this.areaM2,
                    massKg: this.massKg,
                    costTenge: this.finalPriceTenge,
                    costEuro: this.finalPriceTenge / this.euroRate,
                    finalPriceTenge: this.finalPriceTenge
                }
            };

            const btn = document.getElementById("calcAddToCartBtn");
            if (btn) btn.disabled = true;

            try {
                // Вызываем метод api() из cartApp через глобальный scope, либо создадим свой
                const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
                
                const headers = { "Content-Type": "application/json" };
                if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

                const res = await fetch("/cart/virtual/add", {
                    method: "POST",
                    headers: headers,
                    body: JSON.stringify(payload)
                });

                if (!res.ok) throw new Error(await res.text());
                
                // Перезагружаем корзину в основном приложении
                document.dispatchEvent(new CustomEvent('virtual-product-added'));
                alert("Изделие ELCOS добавлено в корзину!");

            } catch (err) {
                alert("Ошибка добавления: " + err.message);
            } finally {
                if (btn) btn.disabled = false;
            }
        }
    }
}

// Регистрируем компонент при инициализации Alpine
document.addEventListener('alpine:init', () => {
    Alpine.data('elcosCalcApp', elcosCalcApp);
});