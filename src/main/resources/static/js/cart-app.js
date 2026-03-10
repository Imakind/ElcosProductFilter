document.addEventListener('alpine:init', () => {
    Alpine.data('cartApp', () => ({
        // --- ДАННЫЕ (State) ---
        folders: [],
        activeFolderId: 1,
        products: [],
        isLoading: true,
        smrName: '',
        smrPrice: '',

        // CSRF Headers for Spring Security
        getCsrfHeaders() {
            const token = document.querySelector('meta[name="_csrf"]')?.content;
            const header = document.querySelector('meta[name="_csrf_header"]')?.content;
            const headers = new Headers();
            if (token && header) headers.set(header, token);
            return headers;
        },

        async api(url, opts={}) {
            const headers = this.getCsrfHeaders();
            if (opts.headers) {
                for (const [key, value] of Object.entries(opts.headers)) {
                    headers.set(key, value);
                }
            }
            const res = await fetch(url, { credentials: 'same-origin', ...opts, headers });
            if (!res.ok) throw new Error(await res.text());
            const ct = res.headers.get('content-type') || '';
            return ct.includes('application/json') ? res.json() : res.text();
        },

        async init() {
            this.isLoading = true;
            try {
                // Fetch folders tree
                await this.loadFolders();

                await this.loadCartItems();

                // Слушаем событие от калькулятора
                document.addEventListener('virtual-product-added', () => {
                    this.loadCartItems();
                });

            } catch (err) {
                console.error("Failed to load cart data:", err);
                // Fallback UI
                this.folders = [{ id: 1, name: 'Общий раздел (Ошибка загрузки)', level: 0 }];
            } finally {
                this.isLoading = false;
            }
        },

        async loadCartItems() {
            const cartData = await this.api('/api/cart/details');
            if (cartData && cartData.items) {
                this.products = cartData.items;
            }
        },

        flattenTree(nodes, level = 0) {
            let result = [];
            nodes.forEach(node => {
                result.push({ 
                    id: node.id, 
                    name: node.name, 
                    level: level 
                });
                if (node.children && node.children.length > 0) {
                    result = result.concat(this.flattenTree(node.children, level + 1));
                }
            });
            return result;
        },

        // --- ВЫЧИСЛЯЕМЫЕ СВОЙСТВА (Getters) ---
        get activeFolderProducts() {
            return this.products.filter(p => p.folderId === this.activeFolderId);
        },

        get activeFolderName() {
            const folder = this.folders.find(f => f.id === this.activeFolderId);
            return folder ? folder.name : '';
        },

        get totalPositions() {
            return this.products.filter(p => p.qty > 0).length;
        },

        get totalQty() {
            return this.products.reduce((sum, p) => sum + (Number(p.qty) || 0), 0);
        },

        get grandTotal() {
            return this.products.reduce((sum, p) => sum + ((Number(p.qty) || 0) * p.price), 0);
        },

        // --- МЕТОДЫ (Actions) ---
        formatMoney(val) {
            return Number(val).toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ₸';
        },

        async changeQty(product, amount) {
            const newRowQty = (Number(product.qty) || 0) + amount;
            if (newRowQty >= 0) {
                // ВАЖНО: Мы отправляем серверу КОЛИЧЕСТВО В ЭТОЙ ПАПКЕ,
                // а сервер сам пересчитает общий остаток в корзине.
                await this.syncQty(product.productId, newRowQty, product.folderId);
                
                if (newRowQty === 0) {
                    this.removeProductFromState(product.id);
                }
                await this.loadCartItems();
            }
        },

        async updateQtyDirectly(product) {
            let newRowQty = Number(product.qty) || 0;
            if (newRowQty < 0) {
                newRowQty = 0;
                product.qty = 0;
            }
            await this.syncQty(product.productId, newRowQty, product.folderId);
            
            if (newRowQty === 0) {
                this.removeProductFromState(product.id);
            }
            await this.loadCartItems();
        },

        async syncQty(productId, qty, sectionId = null) {
            try {
                const params = new URLSearchParams();
                params.set('productId', productId);
                params.set('qty', qty);
                if (sectionId) params.set('sectionId', sectionId);

                await this.api('/cart/set-qty', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: params
                });
            } catch (err) {
                console.error("Failed to update qty on server:", err);
            }
        },

        async removeProduct(id) {
            let pid = id;
            let sid = null;
            if (typeof id === 'string' && id.includes('_')) {
                const parts = id.split('_');
                pid = parts[0];
                sid = parts[1];
            }
            await this.syncQty(pid, 0, sid);
            this.removeProductFromState(id);
            await this.loadCartItems();
        },

        removeProductFromState(id) {
            this.products = this.products.filter(p => p.id !== id);
        },

        selectFolder(id) {
            this.activeFolderId = id;
        },

        getFolderItemCount(folderId) {
            return this.products.filter(p => p.folderId === folderId).length;
        },

        async createFolder(parentId = 1) {
            const name = prompt('Название новой папки:');
            if (name) {
                try {
                    const params = new URLSearchParams();
                    params.set('name', name);
                    params.set('parentId', parentId);

                    const newFolder = await this.api('/cart/sections', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },      
                        body: params
                    });

                    await this.loadFolders();
                } catch (err) {
                    alert('Не удалось создать папку: ' + err.message);
                }
            }
        },

        async renameFolder(folderId, currentName) {
            const newName = prompt('Новое название папки:', currentName);
            if (newName && newName !== currentName) {
                try {
                    const params = new URLSearchParams();
                    params.set('sectionId', folderId);
                    params.set('name', newName);
                    await this.api('/cart/sections/rename', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },      
                        body: params
                    });
                    await this.loadFolders();
                } catch (err) {
                    alert('Не удалось переименовать папку: ' + err.message);
                }
            }
        },

        async deleteFolder(folderId) {
            if (folderId === 1) {
                alert('Нельзя удалить Общий раздел');
                return;
            }
            if (confirm('Вы уверены, что хотите удалить эту папку?')) {
                try {
                    const params = new URLSearchParams();
                    params.set('sectionId', folderId);
                    await this.api('/cart/sections/delete', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },      
                        body: params
                    });

                    if (this.activeFolderId === folderId) {
                        this.activeFolderId = 1;
                    }

                    await this.loadFolders();
                    await this.loadCartItems();
                } catch (err) {
                    alert('Не удалось удалить папку: ' + err.message);
                }
            }
        },

        async clearFolder(folderId) {
            if (confirm('Вы уверены, что хотите удалить ВСЕ товары из этой папки?')) {
                try {
                    const params = new URLSearchParams();
                    params.set('sectionId', folderId);
                    await this.api('/cart/sections/clear', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },      
                        body: params
                    });
                    await this.loadCartItems();
                } catch (err) {
                    alert('Не удалось очистить папку: ' + err.message);
                }
            }
        },

        async moveProductToFolder(id, folderIdStr) {
            try {
                const folderId = Number(folderIdStr);
                // Извлекаем productId из составного id
                const productId = typeof id === 'string' && id.includes('_') ? id.split('_')[0] : id;
                
                const params = new URLSearchParams();
                params.append('productIds', String(productId));
                params.set('sectionId', folderId);
                await this.api('/cart/sections/assign', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },      
                    body: params
                });

                await this.loadCartItems();
            } catch (err) {
                console.error("Failed to move product:", err);
                await this.loadCartItems(); // reload if failed
            }
        },

        // --- Drag & Drop ---
        onDragStart(event, id) {
            event.dataTransfer.setData('text/product-composite-id', String(id));
            event.dataTransfer.effectAllowed = 'move';
        },

        onDragOver(event) {
            event.preventDefault(); // Necessary to allow dropping
            event.dataTransfer.dropEffect = 'move';
        },

        async onDrop(event, folderId) {
            event.preventDefault();
            const id = event.dataTransfer.getData('text/product-composite-id');
            if (id) {
                await this.moveProductToFolder(id, folderId);
            }
        },

        async applyFolderCoeff(folderId) {
            const coeffStr = prompt('Введите новый коэффициент для всех товаров в этой папке (например: 1.2):', '1.0');
            if (!coeffStr) return;
            const coeff = parseFloat(coeffStr.replace(',', '.'));
            if (isNaN(coeff) || coeff <= 0) {
                alert('Некорректный коэффициент');
                return;
            }

            const itemsInFolder = this.products.filter(p => p.folderId === folderId);
            if (itemsInFolder.length === 0) return;

            this.isLoading = true;
            try {
                // Since the backend might not have a bulk endpoint, we do it in a loop
                for (const p of itemsInFolder) {
                    const params = new URLSearchParams();
                    params.set('productId', p.productId);
                    params.set('coefficient', coeff);
                    await this.api('/cart/coefficient', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body: params
                    });
                }
                await this.loadCartItems();
            } catch (err) {
                alert('Ошибка при применении коэффициента: ' + err.message);
            } finally {
                this.isLoading = false;
            }
        },

        async splitProduct(product) {
            if (product.qty <= 1) {
                alert('Нельзя разделить товар с количеством 1 или меньше.');
                return;
            }
            const qtyStr = prompt(`Сколько штук (из ${product.qty}) вы хотите отделить и перенести в другую папку?`);
            if (!qtyStr) return;
            const splitQty = parseInt(qtyStr, 10);
            if (isNaN(splitQty) || splitQty <= 0 || splitQty >= product.qty) {
                alert('Некорректное количество.');
                return;
            }

            // For simplicity, let's just ask for folder ID via prompt, or show a list
            let folderText = "Доступные папки:\n";
            this.folders.forEach(f => {
                folderText += `${f.id} - ${f.name}\n`;
            });
            const targetFolderStr = prompt(folderText + '\nВведите ID папки назначения:');
            if (!targetFolderStr) return;
            const targetFolderId = Number(targetFolderStr);
            if (!this.folders.find(f => f.id === targetFolderId)) {
                 alert('Папка не найдена.');
                 return;
            }

            try {
                const params = new URLSearchParams();
                params.set('productId', product.productId);
                params.set('qty', splitQty);
                params.set('fromSectionId', product.folderId);
                params.set('toSectionId', targetFolderId);
                await this.api('/cart/sections/extract-one', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: params
                });
                await this.loadCartItems();
            } catch (err) {
                alert('Ошибка при разделении: ' + err.message);
            }
        },

        async updateBasePrice(product) {
            const priceStr = prompt('Введите новую базовую цену за 1 шт (в ₸):', product.basePrice);
            if (!priceStr) return;
            const newPrice = parseFloat(priceStr.replace(/[^0-9.,]/g, '').replace(',', '.'));
            if (isNaN(newPrice) || newPrice < 0) {
                alert('Некорректная цена');
                return;
            }

            try {
                const params = new URLSearchParams();
                params.set('productId', product.productId);
                params.set('newPrice', newPrice);
                await this.api('/cart/price/update', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: params
                });
                await this.loadCartItems();
            } catch (err) {
                alert('Ошибка при изменении цены: ' + err.message);
            }
        },

        async addSMR() {
            if (!this.smrName || !this.smrPrice) {
                alert('Заполните все поля для СМР');
                return;
            }

            try {
                const params = new URLSearchParams();
                params.append('name', this.smrName);
                params.append('price', this.smrPrice);
                params.append('sectionId', this.activeFolderId); // Добавляем в активную папку

                await this.api('/cart/smr/add', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: params
                });

                this.smrName = '';
                this.smrPrice = '';
                // Close modal if using bootstrap JS
                const modalEl = document.getElementById('addSMRModal');
                if(modalEl) {
                    const modal = bootstrap.Modal.getInstance(modalEl);
                    if(modal) modal.hide();
                }

                await this.loadCartItems();
            } catch (err) {
                alert('Ошибка при добавлении СМР: ' + err.message);
            }
        },

        async loadFolders() {
            try {
                const treeData = await this.api('/cart/sections/tree');
                if (Array.isArray(treeData) && treeData.length > 0) {
                    this.folders = this.flattenTree(treeData);
                } else {
                    this.folders = [{ id: 1, name: 'Общий раздел', level: 0 }];
                }
            } catch(e) {
                 console.error("Failed to load folders:", e);
                 this.folders = [{ id: 1, name: 'Общий раздел', level: 0 }];
            }
        },

        flattenTree(nodes, level = 0) {
            let result = [];
            nodes.forEach(node => {
                result.push({ 
                    id: node.id, 
                    name: node.name, 
                    level: level 
                });
                if (node.children && node.children.length > 0) {
                    result = result.concat(this.flattenTree(node.children, level + 1));
                }
            });
            return result;
        },
    }));
});
